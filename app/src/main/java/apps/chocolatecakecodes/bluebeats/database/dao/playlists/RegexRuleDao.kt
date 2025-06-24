package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RegexRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed

@Dao
internal abstract class RegexRuleDao {

    @Transaction
    open fun createNew(initialShare: Share): RegexRule {
        val entity = RegexRuleEntity(0, ShareEmbed(initialShare), "", RegexRule.Attribute.TITLE, "")
        val id = insertEntity(entity)
        return RegexRule(id, true, entity.attribute, entity.regex, entity.share.toShare(), "")
    }

    open fun load(id: Long): RegexRule {
        val entity = getEntity(id)
        return RegexRule(
            entity.id, true,
            entity.attribute, entity.regex,
            entity.share.toShare(),
            entity.name
        )
    }

    @Transaction
    open fun save(rule: RegexRule) {
        updateEntity(
            RegexRuleEntity(
                rule.id,
                ShareEmbed(rule.share),
                rule.name,
                rule.attribute,
                rule.regex,
            )
        )
    }

    @Transaction
    open fun delete(rule: RegexRule) {
        delete(rule.id)
    }

    @Transaction
    open fun delete(ruleId: Long) {
        deleteEntity(ruleId)
    }

    //region sql
    @Insert
    protected abstract fun insertEntity(entity: RegexRuleEntity): Long

    @Update
    protected abstract fun updateEntity(entity: RegexRuleEntity)

    @Query("DELETE FROM RegexRuleEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)

    @Query("SELECT * FROM RegexRuleEntity WHERE id = :id;")
    protected abstract fun getEntity(id: Long): RegexRuleEntity
    //endregion
}
