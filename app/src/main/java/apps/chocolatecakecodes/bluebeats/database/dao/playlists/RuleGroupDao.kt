package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RuleGroupEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.RuleGroupEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed
import apps.chocolatecakecodes.bluebeats.util.Utils

@Suppress("NAME_SHADOWING")
@Dao
internal abstract class RuleGroupDao {

    private enum class KnownRuleTypes {
        RULE_GROUP,
        INCLUDE_RULE,
        USERTAGS_RULE,
        ID3TAGS_RULE,
        REGEX_RULE,
        TIMESPAN_RULE
    }

    companion object {
        private fun getRuleType(rule: GenericRule) = when(rule) {
            is RuleGroup -> KnownRuleTypes.RULE_GROUP
            is IncludeRule -> KnownRuleTypes.INCLUDE_RULE
            is UsertagsRule -> KnownRuleTypes.USERTAGS_RULE
            is ID3TagsRule -> KnownRuleTypes.ID3TAGS_RULE
            is RegexRule -> KnownRuleTypes.REGEX_RULE
            is TimeSpanRule -> KnownRuleTypes.TIMESPAN_RULE
        }
    }

    //region api
    @Transaction
    open fun createNew(initialShare: Share): RuleGroup {
        return load(insertEntity(RuleGroupEntity(0, ShareEmbed(initialShare), "", false)))
    }

    fun load(id: Long): RuleGroup {
        return getEntity(id).let(this::loadRuleGroup)
    }

    fun loadAll(): List<RuleGroup> {
        return getAllEntities().map(this::loadRuleGroup)
    }

    @Transaction
    open fun save(group: RuleGroup) {
        if(!group.isOriginal)
            throw IllegalArgumentException("only original rules may be saved to DB")

        val existingRules = getEntriesForGroup(group.id).map {
            Pair(it.rule, KnownRuleTypes.entries[it.type])
        }.toSet()
        val currentRules = group.getRules().associateBy {
            Pair(it.first.id, getRuleType(it.first))
        }

        Utils.diffChanges(existingRules, currentRules.keys).let { (added, deleted, existing) ->
            val buriedRules: MutableSet<Pair<Long, KnownRuleTypes>> = mutableSetOf()
            collectGraveyardRules(group, buriedRules)
            buriedRules.plus(deleted).forEach {
                deleteRule(group.id, it.first, it.second)
            }

            added.forEach {
                val (id, type)  = it
                val ruleWithNegated = currentRules[it]!!
                val (rule, negated) = ruleWithNegated
                val pos = group.getRules().indexOf(ruleWithNegated)

                saveRule(rule)
                insertEntry(RuleGroupEntry(
                    0,
                    group.id,
                    id,
                    type.ordinal,
                    pos,
                    negated
                ))
            }

            existing.forEach {
                val (id, type)  = it
                val ruleWithNegated = currentRules[it]!!
                val (rule, negated) = ruleWithNegated
                val pos = group.getRules().indexOf(ruleWithNegated)

                saveRule(rule)
                updateEntryPos(group.id, id, type.ordinal, pos, negated)
            }
        }

        updateEntity(RuleGroupEntity(group.id, ShareEmbed(group.share), group.name, group.combineWithAnd))
    }

    @Transaction
    open fun delete(group: RuleGroup) {
        group.getRules().map { it.first }.forEach {
            deleteRule(group.id, it)
        }

        deleteEntity(group.id)
    }

    //endregion

    //region sql
    @Insert
    protected abstract fun insertEntity(entity: RuleGroupEntity): Long

    @Update
    protected abstract fun updateEntity(entity: RuleGroupEntity)

    @Query("DELETE FROM RuleGroupEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)

    @Query("SELECT * FROM RuleGroupEntity WHERE id = :id;")
    protected abstract fun getEntity(id: Long): RuleGroupEntity

    @Query("SELECT * FROM RuleGroupEntity;")
    protected abstract fun getAllEntities(): List<RuleGroupEntity>

    @Insert
    protected abstract fun insertEntry(entry: RuleGroupEntry): Long

    @Query("DELETE FROM RuleGroupEntry WHERE rulegroup = :group AND rule = :rule AND type = :type;")
    protected abstract fun deleteEntry(group: Long, rule: Long, type: Int)

    @Query("SELECT * FROM RuleGroupEntry WHERE rulegroup = :group;")
    protected abstract fun getEntriesForGroup(group: Long): List<RuleGroupEntry>

    @Query("UPDATE RuleGroupEntry SET pos = :pos, negated = :negated WHERE rulegroup = :group AND rule = :rule AND type = :type;")
    protected abstract fun updateEntryPos(group: Long, rule: Long, type: Int, pos: Int, negated: Boolean)

    //endregion

    //region private helpers
    private fun loadRuleGroup(entity: RuleGroupEntity): RuleGroup {
        val ruleEntries = getEntriesForGroup(entity.id)
            .sortedBy { it.pos }
            .map { Pair(loadRule(it.rule, KnownRuleTypes.entries[it.type]), it.negated) }

        return RuleGroup(
            entity.id, true, entity.share.toShare(), entity.name, entity.andMode,
            ruleEntries.map { Pair(it.first, it.second) },
        )
    }

    private fun loadRule(id: Long, type: KnownRuleTypes): GenericRule {
        return when(type) {
            KnownRuleTypes.RULE_GROUP -> this.load(id)
            KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().load(id)
            KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().load(id)
            KnownRuleTypes.ID3TAGS_RULE -> RoomDB.DB_INSTANCE.dplID3TagsRuleDao().load(id)
            KnownRuleTypes.REGEX_RULE -> RoomDB.DB_INSTANCE.dplRegexRuleDao().load(id)
            KnownRuleTypes.TIMESPAN_RULE -> RoomDB.DB_INSTANCE.dplTimeSpanRuleDao().load(id)
        }
    }

    /**
     * @return Pair<entityId, type>
     */
    private fun saveRule(rule: GenericRule) {
        when(getRuleType(rule)) {
            KnownRuleTypes.RULE_GROUP -> {
                val rule = rule as RuleGroup
                this.save(rule)
            }
            KnownRuleTypes.INCLUDE_RULE -> {
                val rule = rule as IncludeRule
                val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                dao.save(rule)
            }
            KnownRuleTypes.USERTAGS_RULE -> {
                val rule = rule as UsertagsRule
                val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
                dao.save(rule)
            }
            KnownRuleTypes.ID3TAGS_RULE -> {
                val rule = rule as ID3TagsRule
                val dao = RoomDB.DB_INSTANCE.dplID3TagsRuleDao()
                dao.save(rule)
            }
            KnownRuleTypes.REGEX_RULE -> {
                val rule = rule as RegexRule
                val dao = RoomDB.DB_INSTANCE.dplRegexRuleDao()
                dao.save(rule)
            }
            KnownRuleTypes.TIMESPAN_RULE -> {
                val rule = rule as TimeSpanRule
                val dao = RoomDB.DB_INSTANCE.dplTimeSpanRuleDao()
                dao.save(rule)
            }
        }
    }

    private fun deleteRule(group: Long, rule: GenericRule) {
        when(val type = getRuleType(rule)) {
            KnownRuleTypes.RULE_GROUP -> {
                val rule = rule as RuleGroup
                deleteEntry(group, rule.id, type.ordinal)
                this.delete(rule)
            }
            KnownRuleTypes.INCLUDE_RULE -> {
                val rule = rule as IncludeRule
                val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                deleteEntry(group, rule.id, type.ordinal)
                dao.delete(rule)
            }
            KnownRuleTypes.USERTAGS_RULE -> {
                val rule = rule as UsertagsRule
                val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
                deleteEntry(group, rule.id, type.ordinal)
                dao.delete(rule)
            }
            KnownRuleTypes.ID3TAGS_RULE -> {
                val rule = rule as ID3TagsRule
                val dao = RoomDB.DB_INSTANCE.dplID3TagsRuleDao()
                deleteEntry(group, rule.id, type.ordinal)
                dao.delete(rule)
            }
            KnownRuleTypes.REGEX_RULE -> {
                val rule = rule as RegexRule
                val dao = RoomDB.DB_INSTANCE.dplRegexRuleDao()
                deleteEntry(group, rule.id, type.ordinal)
                dao.delete(rule)
            }
            KnownRuleTypes.TIMESPAN_RULE -> {
                val rule = rule as TimeSpanRule
                val dao = RoomDB.DB_INSTANCE.dplTimeSpanRuleDao()
                deleteEntry(group, rule.id, type.ordinal)
                dao.delete(rule)
            }
        }
    }

    private fun deleteRule(group: Long, ruleId: Long, ruleType: KnownRuleTypes) {
        when(ruleType) {
            KnownRuleTypes.RULE_GROUP -> {
                deleteEntry(group, ruleId, ruleType.ordinal)
                this.delete(this.load(ruleId))
            }
            KnownRuleTypes.INCLUDE_RULE -> {
                val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                deleteEntry(group, ruleId, ruleType.ordinal)
                dao.delete(ruleId)
            }
            KnownRuleTypes.USERTAGS_RULE -> {
                val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
                deleteEntry(group, ruleId, ruleType.ordinal)
                dao.delete(ruleId)
            }
            KnownRuleTypes.ID3TAGS_RULE -> {
                val dao = RoomDB.DB_INSTANCE.dplID3TagsRuleDao()
                deleteEntry(group, ruleId, ruleType.ordinal)
                dao.delete(ruleId)
            }
            KnownRuleTypes.REGEX_RULE -> {
                val dao = RoomDB.DB_INSTANCE.dplRegexRuleDao()
                deleteEntry(group, ruleId, ruleType.ordinal)
                dao.delete(ruleId)
            }
            KnownRuleTypes.TIMESPAN_RULE -> {
                val dao = RoomDB.DB_INSTANCE.dplTimeSpanRuleDao()
                deleteEntry(group, ruleId, ruleType.ordinal)
                dao.delete(ruleId)
            }
        }
    }

    private fun collectGraveyardRules(rule: RuleGroup, output: MutableSet<Pair<Long, KnownRuleTypes>>) {
        rule.graveyard.forEach {
            output.add(Pair(it.id, getRuleType(it)))

            if(it is RuleGroup)
                collectGraveyardRules(it, output)
        }

        rule.graveyard.clear()
    }
    //endregion
}
