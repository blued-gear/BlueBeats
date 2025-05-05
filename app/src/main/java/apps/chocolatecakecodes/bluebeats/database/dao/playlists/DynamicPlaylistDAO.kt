package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.DynamicPlaylistEntity
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit

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
    private val ruleGroupDao: RuleGroupDao by lazy {
        RoomDB.DB_INSTANCE.dplRuleGroupDao()
    }

    @Transaction
    open fun createNew(name: String): DynamicPlaylist {
        val id = playlistsManager.createNewEntry(name, PlaylistType.DYNAMIC)
        val playlist = DynamicPlaylist(id, name, ruleGroupDao.createNew(Rule.Share(1f, true)))
        insertEntity(generateEntity(playlist, id))

        cache.put(id, playlist)
        return playlist
    }

    fun load(id: Long): DynamicPlaylist {
        return cache.get(id) {
            val name = playlistsManager.getPlaylistName(id)
            val entity = getEntity(id)
            val rootRuleGroup = ruleGroupDao.load(entity.ruleRoot)

            DynamicPlaylist(id, name, rootRuleGroup).apply {
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
            playlist.rootRuleGroup.id,
            playlist.iterationSize
        )
    }
}
