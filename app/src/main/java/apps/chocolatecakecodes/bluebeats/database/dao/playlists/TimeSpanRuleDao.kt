package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.TimeSpanRuleEntity

@Dao
internal abstract class TimeSpanRuleDao {

    private val fileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }

    //region api
    @Transaction
    open fun createNew(initialShare: Share): TimeSpanRule {
        val id = insertEntity(TimeSpanRuleEntity(0, ShareEmbed(initialShare), "", null, 0, 0, ""))
        return TimeSpanRule(id, true, MediaNode.INVALID_FILE, 0, 0, "", initialShare, "")
    }

    fun load(id: Long): TimeSpanRule {
        return getEntity(id).let(this::loadRule)
    }

    fun loadAll(): List<TimeSpanRule> {
        return getAllEntities().map(this::loadRule)
    }

    @Transaction
    open fun save(rule: TimeSpanRule) {
        val fileId = if(rule.file === MediaNode.INVALID_FILE)
            null
        else
            rule.file.id

        updateEntity(TimeSpanRuleEntity(rule.id, ShareEmbed(rule.share), rule.name, fileId,
            rule.startMs, rule.endMs, rule.description))
    }

    fun delete(rule: TimeSpanRule) {
        delete(rule.id)
    }

    @Transaction
    open fun delete(id: Long) {
        deleteEntity(id)
    }
    //endregion

    //region sql
    @Query("SELECT * FROM TimeSpanRuleEntity WHERE id = :id;")
    protected abstract fun getEntity(id: Long): TimeSpanRuleEntity

    @Query("SELECT * FROM TimeSpanRuleEntity;")
    protected abstract fun getAllEntities(): List<TimeSpanRuleEntity>

    @Insert
    protected abstract fun insertEntity(entity: TimeSpanRuleEntity): Long

    @Update
    protected abstract fun updateEntity(entity: TimeSpanRuleEntity)

    @Query("DELETE FROM TimeSpanRuleEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)
    //endregion

    //region private helpers
    private fun loadRule(entity: TimeSpanRuleEntity): TimeSpanRule {
        val file = entity.file?.let {
            fileDao.getForId(it)
        } ?: MediaNode.INVALID_FILE

        return TimeSpanRule(entity.id, true, file,
            entity.startMs, entity.endMs,
            entity.desc, entity.share.toShare(), entity.name)
    }
    //endregion
}
