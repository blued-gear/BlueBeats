package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.StaticPlaylist
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.StaticPlaylistEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.StaticPlaylistEntry
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import apps.chocolatecakecodes.bluebeats.util.serializers.PlaylistItemSerializer
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit

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
        val id = playlistsManager.createNewEntry(name, PlaylistType.STATIC)
        insertEntity(StaticPlaylistEntity(id))

        return StaticPlaylist(id, name, emptyList()).also {
            cache.put(id, it)
        }
    }

    fun load(id: Long): StaticPlaylist {
        return cache.get(id) {
            val name = playlistsManager.getPlaylistName(id)

            val entries = getEntriesForPlaylist(id).sortedBy {
                it.pos
            }.map {
                PlaylistItemSerializer.INSTANCE.deserialize(it.item)
            }

            return@get StaticPlaylist(id, name, entries)
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
