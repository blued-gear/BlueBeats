package apps.chocolatecakecodes.bluebeats.media.playlist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

/**
 * a normal playlist where the user puts media in manually
 */
internal class StaticPlaylist private constructor(
    private val entity: StaticPlaylistEntity,
    entries: List<MediaFile>
    ) : Playlist {

    override val type: PlaylistType = PlaylistType.STATIC

    override val name: String
        get() = entity.toString()

    private val media: ArrayList<MediaFile> = ArrayList(entries)
    private val mediaImmutable: List<MediaFile> = Collections.unmodifiableList(media)

    override fun items(): List<MediaFile> {
        return mediaImmutable
    }

    override fun getIterator(repeat: Boolean, shuffle: Boolean): PlaylistIterator {
        return StaticPlaylistIterator(media, repeat, shuffle)
    }

    fun addMedia(toAdd: MediaFile){
        if(media.contains(toAdd))
            return
        media.add(toAdd)
    }

    fun removeMedia(toRemove: MediaFile){
        media.remove(toRemove)
    }

    fun moveMedia(toMove: MediaFile, newIndex: Int){
        if(!media.remove(toMove))
            throw IllegalArgumentException("playlist does not contain this media")
        media.add(newIndex, toMove)
    }

    // DAO as internal class or else entity would have to be internal
    @Dao
    internal abstract class StaticPlaylistDao {

        private val cache: Cache<Long, StaticPlaylist>

        init{
            cache = CacheBuilder.newBuilder().weakValues().build()
        }

        fun createNew(name: String): StaticPlaylist {
            val id = insertEntity(StaticPlaylistEntity(0, name))
            return load(id)
        }

        fun load(id: Long): StaticPlaylist {
            return cache.get(id){
                val entity = getEntityWithId(id)
                val entries = getEntriesForPlaylist(id).map {
                    RoomDB.DB_INSTANCE.mediaFileDao().getForId(it.media)
                }
                StaticPlaylist(entity, entries)
            }
        }

        fun save(playlist: StaticPlaylist) {
            updateEntity(playlist.entity)

            deleteEntriesOfPlaylist(playlist.entity.id)
            playlist.items().map {
                StaticPlaylistEntry(0, playlist.entity.id, it.entity.id)
            }.let {
                insertPlaylistEntries(it)
            }
        }

        fun changeName(playlist: StaticPlaylist, newName: String) {
            val entity = playlist.entity
            val oldName = entity.name

            entity.name = newName
            try {
                updateEntity(entity)
            } catch (e: Exception) {
                entity.name = oldName

                throw e
            }
        }

        /**
         * return the names of all playlists with their IDs
         */
        fun getAllNames(): Map<String, Long> {
            return getAllEntities().associate {
                Pair(it.name, it.id)
            }
        }

        @Query("SELECT * FROM StaticPlaylistEntity;")
        protected abstract fun getAllEntities(): List<StaticPlaylistEntity>

        @Query("SELECT * FROM StaticPlaylistEntity WHERE id = :id;")
        protected abstract fun getEntityWithId(id: Long): StaticPlaylistEntity

        @Insert
        protected abstract fun insertEntity(entity: StaticPlaylistEntity): Long

        @Update
        protected abstract fun updateEntity(entity: StaticPlaylistEntity)

        @Query("SELECT * FROM StaticPlaylistEntry WHERE playlist = :id;")
        protected abstract fun getEntriesForPlaylist(id: Long): List<StaticPlaylistEntry>

        @Query("DELETE FROM StaticPlaylistEntry WHERE playlist = :playlist;")
        protected abstract fun deleteEntriesOfPlaylist(playlist: Long)

        @Insert
        protected abstract fun insertPlaylistEntries(entries: List<StaticPlaylistEntry>)
    }
}

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
internal data class StaticPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name") var name: String,
)

@Entity
internal data class StaticPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "playlist", index = true) val playlist: Long,
    @ColumnInfo(name = "media") val media: Long
)

private class StaticPlaylistIterator(
    media: List<MediaFile>,
    private val repeat: Boolean,
    private val shuffle: Boolean
    ) : PlaylistIterator {

    private val items = ArrayList(media)

    override val totalItems: Int = items.size
    override var currentPosition: Int = -1

    init {
        if(shuffle)
            items.shuffle()
    }

    override fun nextMedia(): MediaFile {
        if(isAtEnd())
            throw IllegalStateException("end reached")

        currentPosition++
        if(currentPosition == totalItems) {// not checking for repeat because it is done in isAtEnd()
            currentPosition = 0
            if(shuffle)
                items.shuffle()
        }

        return items[currentPosition]
    }

    override fun isAtEnd(): Boolean {
        return !repeat && currentPosition == totalItems
    }
}
