package apps.chocolatecakecodes.bluebeats.media.playlist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItemSerializer
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * a normal playlist where the user puts media in manually
 */
internal class StaticPlaylist private constructor(
    name: String,
    entries: List<PlaylistItem>
) : Playlist {

    override val type: PlaylistType = PlaylistType.STATIC

    override var name: String = name
        private set

    private val media: ArrayList<PlaylistItem> = ArrayList(entries)
    private val mediaImmutable: List<PlaylistItem> = Collections.unmodifiableList(media)

    override fun items(): List<PlaylistItem> {
        return mediaImmutable
    }

    override fun getIterator(repeat: PlaylistIterator.RepeatMode, shuffle: Boolean): PlaylistIterator {
        val validMedia = media.filter { it !is PlaylistItem.INVALID }
        return StaticPlaylistIterator(validMedia, repeat, shuffle)
    }

    fun addMedia(toAdd: MediaFile) {
        media.add(MediaFileItem(toAdd))
    }

    fun removeMedia(index: Int) {
        media.removeAt(index)
    }

    fun moveMedia(from: Int, newIndex: Int) {
        val item = media.removeAt(from)
        media.add(newIndex, item)
    }

    // DAO as internal class or else some setters would have to be internal
    @Dao
    internal abstract class StaticPlaylistDao {

        private val cache: Cache<Long, StaticPlaylist>

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

        @Transaction
        open fun createNew(name: String): StaticPlaylist {
            val playlist = StaticPlaylist(name, emptyList<PlaylistItem>())
            val id = playlistsManager.createNewEntry(name, playlist.type)
            insertEntity(StaticPlaylistEntity(id))

            cache.put(id, playlist)
            return playlist
        }

        fun load(id: Long): StaticPlaylist {
            return cache.get(id) {
                val name = playlistsManager.getPlaylistName(id)

                val entries = getEntriesForPlaylist(id).sortedBy {
                    it.pos
                }.map {
                    PlaylistItemSerializer.INSTANCE.deserialize(it.item)
                }

                StaticPlaylist(name, entries)
            }
        }

        @Transaction
        open fun save(playlist: StaticPlaylist) {
            val id = playlistsManager.getPlaylistId(playlist.name)

            deleteEntriesOfPlaylist(id)
            playlist.items().mapIndexed { idx, item ->
                val itemJson = PlaylistItemSerializer.INSTANCE.serialize(item)
                StaticPlaylistEntry(0, id, itemJson, idx)
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
    @PrimaryKey(autoGenerate = false) val id: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = StaticPlaylistEntity::class,
            parentColumns = ["id"], childColumns = ["playlist"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["playlist"])
    ]
)
internal data class StaticPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "playlist") val playlist: Long,
    @ColumnInfo(name = "item") val item: String,
    @ColumnInfo(name = "pos") val pos: Int
)

private class StaticPlaylistIterator(
    media: List<PlaylistItem>,
    override var repeat: PlaylistIterator.RepeatMode,
    shuffle: Boolean
) : PlaylistIterator {

    private val items = ArrayList(media)
    private val itemsRO = Collections.unmodifiableList(items)

    override val totalItems: Int = items.size
    override var currentPosition: Int = -1
        private set

    override var shuffle: Boolean = shuffle
        set(value) {
            field = value

            if(value)
                shuffle()
        }

    init {
        if(shuffle)
            shuffle()
    }

    override fun nextItem(): PlaylistItem {
        if(isAtEnd())
            throw NoSuchElementException("end reached")

        if(repeat != PlaylistIterator.RepeatMode.ONE)
            seek(1)

        return items[currentPosition]
    }

    override fun currentItem(): PlaylistItem {
        return items[currentPosition.coerceAtLeast(0)]
    }

    /**
     * seek relative to the current position
     * (use negative amount for seeking backward)
     *
     * if repeat is true then seeking to one after the last item is allowed;
     *  this will result to reset the iterator to the first item
     *
     * @throws IllegalArgumentException if seeking results in out-of-bounds
     */
    override fun seek(amount: Int) {
        if(amount == 0) return

        val newPos = currentPosition + amount

        if(newPos == totalItems && repeat == PlaylistIterator.RepeatMode.ALL) {
            currentPosition = 0

            if(shuffle)
                shuffle()
        } else if(newPos >= 0 && newPos < totalItems) {
            currentPosition = newPos
        } else {
            throw IllegalArgumentException("seeking by $amount would result in an out-of-bounds position")
        }
    }

    override fun isAtEnd(): Boolean {
        return repeat == PlaylistIterator.RepeatMode.NONE && currentPosition == (totalItems - 1)
    }

    override fun getItems(): List<PlaylistItem> {
        return itemsRO
    }

    private fun shuffle() {
        // current media must stay at same index
        if(currentPosition == -1) {
            items.shuffle()
        } else {
            val media = items.removeAt(currentPosition)
            items.shuffle()
            items.add(currentPosition, media)
        }
    }
}
