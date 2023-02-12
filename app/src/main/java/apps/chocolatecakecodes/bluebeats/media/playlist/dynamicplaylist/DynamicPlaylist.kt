package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.*
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

private const val EXAMPLE_ITEM_COUNT = 50

internal class DynamicPlaylist private constructor(
    name: String,
    val rootRuleGroup: RuleGroup
) : Playlist {

    override val type: PlaylistType = PlaylistType.DYNAMIC

    override var name: String = name
        private set

    /**
     * the minimum size after which media is allowed to repeat
     * (this determines how large the media-buffer will be before the next items will be generated)
     */
    var iterationSize = EXAMPLE_ITEM_COUNT

    override fun items(): List<MediaFile> {
        return rootRuleGroup.generateItems(iterationSize.coerceAtLeast(EXAMPLE_ITEM_COUNT), emptySet())
    }

    override fun getIterator(repeat: Boolean, shuffle: Boolean): PlaylistIterator {
        return DynamicPlaylistIterator(rootRuleGroup, iterationSize)
    }

    // DAO as internal class or else some setters would have to be internal
    @Dao
    internal abstract class DynamicPlaylistDAO {

        private val cache: Cache<Long, DynamicPlaylist>

        init{
            cache = CacheBuilder.newBuilder().weakValues().build()
            TimerThread.INSTANCE.addInterval(TimeUnit.MINUTES.toMillis(5)) {
                cache.cleanUp()
                0
            }
        }

        private val playlistsManager: PlaylistsManager by lazy {
            RoomDB.DB_INSTANCE.playlistManager()
        }
        private val ruleGroupDao: RuleGroup.RuleGroupDao by lazy {
            RoomDB.DB_INSTANCE.dplRuleGroupDao()
        }

        @Transaction
        open fun createNew(name: String): DynamicPlaylist {
            val playlist = DynamicPlaylist(name, ruleGroupDao.createNew(Rule.Share(1f, true)))
            val id = playlistsManager.createNewEntry(name, playlist.type)
            insertEntity(generateEntity(playlist, id))

            cache.put(id, playlist)
            return playlist
        }

        fun load(id: Long): DynamicPlaylist {
            return cache.get(id) {
                val name = playlistsManager.getPlaylistName(id)
                val entity = getEntity(id)
                val rootRuleGroup = ruleGroupDao.load(entity.ruleRoot)

                DynamicPlaylist(name, rootRuleGroup).apply {
                    iterationSize = entity.iterationSize
                }
            }
        }

        @Transaction
        open fun save(playlist: DynamicPlaylist) {
            ruleGroupDao.save(playlist.rootRuleGroup)
            updateEntity(generateEntity(playlist, -1))
        }

        @Transaction
        open fun delete(playlist: DynamicPlaylist) {
            delete(playlistsManager.getPlaylistId(playlist.name))
        }

        @Transaction
        open fun delete(id: Long) {
            val entity = getEntity(id)
            deleteEntity(entity)
            ruleGroupDao.delete(ruleGroupDao.load(entity.ruleRoot))
            playlistsManager.deleteEntry(id)
        }

        fun changeName(playlist: DynamicPlaylist, newName: String) {
            playlistsManager.renamePlaylist(playlist.name, newName)
            playlist.name = newName
        }

        @Insert
        protected abstract fun insertEntity(entity: DynamicPlaylistEntity): Long

        @Delete
        protected abstract fun deleteEntity(entity: DynamicPlaylistEntity)

        @Update
        protected abstract fun updateEntity(entity: DynamicPlaylistEntity)

        @Query("SELECT * FROM DynamicPlaylistEntity WHERE id = :id;")
        protected abstract fun getEntity(id: Long): DynamicPlaylistEntity

        /**
         * generates a new entity for the given playlist
         * @param id the id to use or -1 to resolve the id by the name of the playlist
         */
        private fun generateEntity(playlist: DynamicPlaylist, id: Long): DynamicPlaylistEntity {
            return DynamicPlaylistEntity(
                if(id == -1L) playlistsManager.getPlaylistId(playlist.name) else id,
                ruleGroupDao.getEntityId(playlist.rootRuleGroup),
                playlist.iterationSize
            )
        }
    }
}

internal class DynamicPlaylistIterator(
    private val rootRuleGroup: RuleGroup,
    private val bufferSize: Int
) : PlaylistIterator {

    private val mediaBuffer = ArrayList<MediaFile>(bufferSize + 2)
    private val mediaBufferRO = Collections.unmodifiableList(mediaBuffer)
    private val pregeneratedMediaBuffer = ArrayList<MediaFile>(bufferSize + 1)
    @Volatile
    private var pregeneratedMediaBufferValid: Boolean = false

    override val totalItems: Int = UNDETERMINED_COUNT
    override var currentPosition: Int = -1
        private set

    @Suppress("SuspiciousVarProperty")
    override var repeat: Boolean = true
        get() = true
    @Suppress("SetterBackingFieldAssignment")
    override var shuffle: Boolean = true
        set(_) {
            // used to trigger regeneration
            val current = currentMedia()
            generateItems(mediaBuffer, currentMedia())
            seekToMedia(current)
        }

    init {
        generateItems(mediaBuffer, null)
        currentPosition = -1
    }

    override fun nextMedia(): MediaFile {
        seek(1)
        return mediaBuffer[currentPosition]
    }

    override fun currentMedia(): MediaFile {
        return mediaBuffer[currentPosition.coerceAtLeast(0)]
    }

    override fun seek(amount: Int) {
        if(amount == 0) return

        val newPos = currentPosition + amount

        if(newPos == mediaBuffer.size) {
            if(pregeneratedMediaBufferValid) {
                mediaBuffer.clear()
                mediaBuffer.addAll(pregeneratedMediaBuffer)
                pregeneratedMediaBufferValid = false
            } else {
                generateItems(mediaBuffer, mediaBuffer.last())// retain last media and place at top
            }

            currentPosition = 0
        } else if(newPos >= 0 && newPos < mediaBuffer.size) {
            currentPosition = newPos

            // pregenerate items if near end of current buffer
            if(currentPosition == mediaBuffer.size - 2){
                CoroutineScope(Dispatchers.IO).launch {
                    pregeneratedMediaBufferValid = false
                    generateItems(pregeneratedMediaBuffer, mediaBuffer.last())// retain last media and place at top
                    pregeneratedMediaBufferValid = true
                }
            }
        } else {
            throw IllegalArgumentException("seeking by $amount would result in an out-of-bounds position")
        }
    }

    /**
     * Tries to find the media in the current buffer and selects it as the next media.
     * If the media could not be found in the buffer, it will be inserted after the current item.
     */
    fun seekToMedia(media: MediaFile) {
        val idx = mediaBuffer.indexOf(media)
        if(idx != -1) {
            currentPosition = idx - 1
        } else {
            mediaBuffer.add(currentPosition + 1, media)
        }
    }

    override fun isAtEnd(): Boolean {
        return false
    }

    override fun getItems(): List<MediaFile> {
        return mediaBufferRO
    }

    private fun generateItems(dest: MutableList<MediaFile>, prepend: MediaFile?) {
        val toExclude = if(prepend !== null) setOf(prepend) else emptySet()// exclude to prevent repetition

        dest.clear()
        if(prepend != null)
            dest.add(prepend)
        dest.addAll(rootRuleGroup.generateItems(bufferSize, toExclude).shuffled())
    }
}

//region entities
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = RuleGroupEntity::class,
            parentColumns = ["id"], childColumns = ["ruleRoot"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ]
)
internal data class DynamicPlaylistEntity(
    @PrimaryKey(autoGenerate = false) val id: Long,
    val ruleRoot: Long,
    val iterationSize: Int
)
//endregion
