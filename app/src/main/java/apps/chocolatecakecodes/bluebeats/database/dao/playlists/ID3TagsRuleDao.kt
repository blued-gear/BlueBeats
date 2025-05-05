package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ID3TagsRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ID3TagsRuleEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed
import apps.chocolatecakecodes.bluebeats.util.Utils

@Dao
internal abstract class ID3TagsRuleDao {

    //region public methods
    @Transaction
    open fun create(initialShare: Rule.Share): ID3TagsRule {
        return insertEntity(ID3TagsRuleEntity(0, ShareEmbed(initialShare), "")).let {
            ID3TagsRule(initialShare, true, it)
        }
    }

    fun load(id: Long): ID3TagsRule {
        val rule = findEntityWithId(id).let {
            ID3TagsRule(it.share.toShare(), true, it.id).apply {
                tagType = it.tagType
            }
        }

        findEntriesForRule(id).forEach {
            rule.addTagValue(it.value)
        }

        return rule
    }

    @Transaction
    open fun save(rule: ID3TagsRule) {
        updateEntity(ID3TagsRuleEntity(rule.id, ShareEmbed(rule.share), rule.tagType))

        Utils.diffChanges(
            findEntriesForRule(rule.id).map { it.value }.toSet(),
            rule.getTagValues()
        ).let { (added, removed, _) ->
            added.forEach {
                insertEntry(ID3TagsRuleEntry(0, rule.id, it))
            }

            removed.forEach {
                deleteEntry(rule.id, it)
            }
        }
    }

    fun delete(rule: ID3TagsRule) {
        delete(rule.id)
    }

    @Transaction
    open fun delete(id: Long) {
        deleteAllEntriesForRule(id)
        deleteEntity(id)
    }
    //endregion

    //region db actions
    @Query("SELECT * FROM ID3TagsRuleEntity WHERE id = :id;")
    protected abstract fun findEntityWithId(id: Long): ID3TagsRuleEntity

    @Query("SELECT * FROM ID3TagsRuleEntry WHERE rule = :rule;")
    protected abstract fun findEntriesForRule(rule: Long): List<ID3TagsRuleEntry>

    @Insert
    protected abstract fun insertEntity(entity: ID3TagsRuleEntity): Long

    @Insert
    protected abstract fun insertEntry(entity: ID3TagsRuleEntry): Long

    @Update
    protected abstract fun updateEntity(entity: ID3TagsRuleEntity)

    @Query("DELETE FROM ID3TagsRuleEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)

    @Query("DELETE FROM ID3TagsRuleEntry WHERE rule = :rule AND value = :value;")
    protected abstract fun deleteEntry(rule: Long, value: String)

    @Query("DELETE FROM ID3TagsRuleEntry WHERE rule = :rule;")
    protected abstract fun deleteAllEntriesForRule(rule: Long)
    //endregion
}
