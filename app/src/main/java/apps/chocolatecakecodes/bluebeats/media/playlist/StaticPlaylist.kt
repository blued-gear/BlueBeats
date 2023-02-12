package apps.chocolatecakecodes.bluebeats.media.playlist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.*
import kotlin.collections.ArrayList

/**
 * a normal playlist where the user puts media in manually
 */
internal class StaticPlaylist private constructor(
    name: String,
    entries: List<MediaFile>
) : Playlist {

    override val type: PlaylistType = PlaylistType.STATIC

    override var name: String = name
        private set

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

    fun removeMedia(index: Int){
        media.removeAt(index)
    }

    fun moveMedia(from: Int, newIndex: Int){
        val item = media.removeAt(from)
        media.add(newIndex, item)
    }

    // DAO as internal class or else entity would have to be internal
    @Dao
    internal abstract class StaticPlaylistDao {

        private val cache: Cache<Long, StaticPlaylist>

        init{
            cache = CacheBuilder.newBuilder().weakValues().build()
        }

        private val playlistsManager: PlaylistsManager by lazy {
            RoomDB.DB_INSTANCE.playlistManager()
        }

        @Transaction
        open fun createNew(name: String): StaticPlaylist {
            val playlist = StaticPlaylist(name, emptyList())
            val id = insertEntity(StaticPlaylistEntity(0))

            playlistsManager.createNewEntry(name, playlist.type, id)

            cache.put(id, playlist)
            return playlist
        }

        fun load(id: Long): StaticPlaylist {
            return cache.get(id){
                val name = playlistsManager.getPlaylistName(id)

                val entries = getEntriesForPlaylist(id).sortedBy {
                    it.pos
                }.map {
                    RoomDB.DB_INSTANCE.mediaFileDao().getForId(it.media)
                }

                StaticPlaylist(name, entries)
            }
        }

        @Transaction
        open fun save(playlist: StaticPlaylist) {
            val id = playlistsManager.getPlaylistId(playlist.name)

            deleteEntriesOfPlaylist(id)
            playlist.items().mapIndexed { idx, media ->
                StaticPlaylistEntry(0, id, media.entity.id, idx)
            }.let {
                insertPlaylistEntries(it)
            }
        }

        @Transaction
        open fun delete(playlist: StaticPlaylist) {
            delete(playlistsManager.getPlaylistId(playlist.name))
        }

        @Transaction
        open fun delete(playlistId: Long) {
            playlistsManager.deleteEntry(playlistId)
            deleteEntries(playlistId)
            deleteEntity(StaticPlaylistEntity(playlistId))

            cache.invalidate(playlistId)
        }

        fun changeName(playlist: StaticPlaylist, newName: String) {
            playlistsManager.renamePlaylist(playlist.name, newName)
            playlist.name = newName
        }

        @Insert
        protected abstract fun insertEntity(entity: StaticPlaylistEntity): Long

        @Delete
        protected abstract fun deleteEntity(entity: StaticPlaylistEntity)

        @Query("SELECT * FROM StaticPlaylistEntry WHERE playlist = :id;")
        protected abstract fun getEntriesForPlaylist(id: Long): List<StaticPlaylistEntry>

        @Query("DELETE FROM StaticPlaylistEntry WHERE playlist = :playlist;")
        protected abstract fun deleteEntriesOfPlaylist(playlist: Long)

        @Insert
        protected abstract fun insertPlaylistEntries(entries: List<StaticPlaylistEntry>)

        @Query("DELETE FROM StaticPlaylistEntry WHERE playlist = :playlist;")
        protected abstract fun deleteEntries(playlist: Long)
    }
}

@Entity
internal data class StaticPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = StaticPlaylistEntity::class,
            parentColumns = ["id"], childColumns = ["playlist"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["media"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE//TODO maybe remap to _DELETE_ITEM_ entry
        )
    ]
)
internal data class StaticPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "playlist", index = true) val playlist: Long,
    @ColumnInfo(name = "media") val media: Long,
    @ColumnInfo(name = "pos") val pos: Int
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