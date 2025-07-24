package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.UsertagsRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.UsertagsRuleEntry
import apps.chocolatecakecodes.bluebeats.util.Utils

@Dao
internal abstract class UsertagsRuleDao{

    //region public methods
    @Transaction
    open fun createNew(share: Share): UsertagsRule {
        val initialAndMode = true
        val id = insertEntity(UsertagsRuleEntity(0, ShareEmbed(share), "", initialAndMode))
        return UsertagsRule(share, initialAndMode, true, id, "")
    }

    fun load(id: Long): UsertagsRule {
        return getEntity(id).let(this::loadRule)
    }

    fun loadAll(): List<UsertagsRule> {
        return getAllEntities().map(this::loadRule)
    }

    @Transaction
    open fun save(rule: UsertagsRule) {
        if(!rule.isOriginal)
            throw IllegalArgumentException("only original rules may be saved to DB")

        val storedTags = getAllEntriesForRule(rule.id).map { it.tag }.toSet()
        Utils.diffChanges(storedTags, rule.getTags()).let { (added, deleted, _) ->
            deleted.forEach {
                deleteEntry(rule.id, it)
            }
            added.forEach {
                insertEntry(UsertagsRuleEntry(0, rule.id, it))
            }
        }

        updateEntity(UsertagsRuleEntity(rule.id, ShareEmbed(rule.share), rule.name, rule.combineWithAnd))
    }

    fun delete(rule: UsertagsRule) {
        delete(rule.id)
    }

    @Transaction
    open fun delete(id: Long) {
        deleteAllEntriesForRule(id)
        deleteEntity(id)
    }
    //endregion

    //region sql
    @Insert
    abstract fun insertEntity(entity: UsertagsRuleEntity): Long

    @Update
    abstract fun updateEntity(entity: UsertagsRuleEntity)

    @Insert
    abstract fun insertEntry(entry: UsertagsRuleEntry)

    @Query("SELECT * FROM UsertagsRuleEntity WHERE id = :id;")
    abstract fun getEntity(id: Long): UsertagsRuleEntity

    @Query("SELECT * FROM UsertagsRuleEntity;")
    abstract fun getAllEntities(): List<UsertagsRuleEntity>

    @Query("SELECT * FROM UsertagsRuleEntry WHERE rule = :rule;")
    abstract fun getAllEntriesForRule(rule: Long): List<UsertagsRuleEntry>

    @Query("DELETE FROM UsertagsRuleEntry WHERE rule = :rule;")
    abstract fun deleteAllEntriesForRule(rule: Long)

    @Query("DELETE FROM UsertagsRuleEntity WHERE id = :id;")
    abstract fun deleteEntity(id: Long)

    @Query("DELETE FROM UsertagsRuleEntry WHERE rule = :rule AND tag = :tag;")
    abstract fun deleteEntry(rule: Long, tag: String)
    //endregion

    //region private helpers
    private fun loadRule(entity: UsertagsRuleEntity): UsertagsRule {
        return entity.let {
            UsertagsRule(it.share.toShare(), it.andMode, true, it.id, it.name)
        }.apply {
            getAllEntriesForRule(this.id).forEach {
                this.addTag(it.tag)
            }
        }
    }
    //endregion
}
