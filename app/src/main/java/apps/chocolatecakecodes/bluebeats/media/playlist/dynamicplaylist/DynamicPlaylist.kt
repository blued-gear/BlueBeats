package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.*
import apps.chocolatecakecodes.bluebeats.media.playlist.Playlist
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistsManager
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.*
import kotlin.collections.ArrayList

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
        return rootRuleGroup.generateItems(iterationSize.coerceAtLeast(EXAMPLE_ITEM_COUNT))
    }

    override fun getIterator(repeat: Boolean, shuffle: Boolean): PlaylistIterator {
        return DynamicPlaylistIterator(rootRuleGroup, iterationSize).apply {
            this.repeat = repeat
            this.shuffle = shuffle
        }
    }

    // DAO as internal class or else some setters would have to be internal
    @Dao
    internal abstract class DynamicPlaylistDAO {

        private val cache: Cache<Long, DynamicPlaylist>

        init{
            cache = CacheBuilder.newBuilder().weakValues().build()
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
            val id = insertEntity(generateEntity(playlist, false))

            playlistsManager.createNewEntry(name, PlaylistType.DYNAMIC, id)

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
            updateEntity(generateEntity(playlist, true))
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

        private fun generateEntity(playlist: DynamicPlaylist, resolveId: Boolean): DynamicPlaylistEntity {
            return DynamicPlaylistEntity(
                if(resolveId) playlistsManager.getPlaylistId(playlist.name) else 0,
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

    private val mediaBuffer = ArrayList<MediaFile>(bufferSize + 1)
    private val mediaBufferRO = Collections.unmodifiableList(mediaBuffer)

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
            generateItems()
        }

    init {
        generateItems()
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
            generateItems()
        } else if(newPos >= 0 && newPos < totalItems) {
            currentPosition = newPos
        } else {
            throw IllegalArgumentException("seeking by $amount would result in an out-of-bounds position")
        }
    }

    override fun isAtEnd(): Boolean {
        return false
    }

    override fun getItems(): List<MediaFile> {
        return mediaBufferRO
    }

    private fun generateItems() {
        // retain current media (if existing) and place at top
        val currentMedia: MediaFile?
        val toExclude: ExcludeRule
        if(currentPosition >= 0) {
            currentMedia = currentMedia()
            toExclude = ExcludeRule.temporaryExclude(files = setOf(currentMedia))// exclude to prevent repetition
        } else {
            currentMedia = null
            toExclude = ExcludeRule.EMPTY_EXCLUDE
        }

        mediaBuffer.clear()
        mediaBuffer.addAll(rootRuleGroup.generateItems(bufferSize, toExclude).shuffled())

        if(currentMedia !== null)
            mediaBuffer.add(0, currentMedia)

        currentPosition = 0
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
    @PrimaryKey(autoGenerate = true) val id: Long,
    val ruleRoot: Long,
    val iterationSize: Int
)
//endregion
