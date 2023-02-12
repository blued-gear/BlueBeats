package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import java.util.*

internal class RuleGroup private constructor(
    private val entityId: Long,
    override var share: Rule.Share,
    rules: List<Rule> = emptyList(),
    excludes: List<ExcludeRule> = emptyList()
) : Rule {

    private val rules = ArrayList(rules)
    private val rulesRO: List<Rule> by lazy {
        Collections.unmodifiableList(this.rules)
    }
    private val excludes = ArrayList(excludes)
    private val excludesRO: List<ExcludeRule> by lazy {
        Collections.unmodifiableList(this.excludes)
    }

    override fun generateItems(amount: Int, exclude: ExcludeRule): List<MediaFile> {
        val toExclude = getExcludes().fold(exclude) { a, b ->
            a.union(b)
        }

        val (relativeRules, absoluteRules) = getRules().partition { it.share.isRelative }

        val absoluteItems = absoluteRules.flatMap {
            it.generateItems(it.share.value.toInt(), toExclude)
        }.take(amount)

        val relativeAmount = amount - absoluteItems.size
        val relativeItems = relativeRules.flatMap {
            it.generateItems((relativeAmount * it.share.value).toInt(), toExclude)
        }

        return (absoluteItems + relativeItems).take(amount)
    }

    fun getRules(): List<Rule> {
        return rulesRO
    }

    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    fun removeRule(rule: Rule) {
        rules.remove(rule)
    }

    fun removeRuleAt(idx: Int) {
        rules.removeAt(idx)
    }

    fun getExcludes(): List<ExcludeRule> {
        return excludesRO
    }

    fun addExclude(rule: ExcludeRule) {
        excludes.add(rule)
    }

    fun removeExclude(rule: ExcludeRule) {
        excludes.remove(rule)
    }

    fun removeExcludeAt(idx: Int) {
        excludes.removeAt(idx)
    }

    fun getExcludesAndRules() : List<Rulelike> {
        return excludesRO + rulesRO
    }

    fun addRule(rule: Rulelike) {
        when (rule) {
            is ExcludeRule -> addExclude(rule)
            is Rule -> addRule(rule)
            else -> throw IllegalArgumentException("unsupported rule type")
        }
    }

    fun removeRule(rule: Rulelike) {
        when (rule) {
            is ExcludeRule -> removeExclude(rule)
            is Rule -> removeRule(rule)
            else -> throw IllegalArgumentException("unsupported rule type")
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is RuleGroup)
            return false

        return this.getExcludesAndRules() == other.getExcludesAndRules()
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, getExcludes(), getRules())
    }

    @Suppress("NAME_SHADOWING")
    @Dao
    internal abstract class RuleGroupDao {

        private enum class KnownRuleTypes {
            RULE_GROUP,
            EXCLUDE_RULE,
            INCLUDE_RULE
        }

        //region api
        @Transaction
        open fun createNew(initialShare: Rule.Share): RuleGroup {
            return load(insertEntity(RuleGroupEntity(0, initialShare)))
        }

        fun load(id: Long): RuleGroup {
            val entity = getEntity(id)
            val (excludeEntries, ruleEntries) = getEntriesForGroup(id)
                .sortedBy { it.pos }
                .map { loadRule(it.rule, KnownRuleTypes.values()[it.type]) }
                .partition { it is ExcludeRule }

            return RuleGroup(
                entity.id, entity.share,
                ruleEntries.map { it as Rule },
                excludeEntries.map { it as ExcludeRule }
            )
        }

        @Transaction
        open fun save(group: RuleGroup) {
            val existingRules = getEntriesForGroup(group.entityId).map {
                Pair(it.rule, KnownRuleTypes.values()[it.type])
            }.toSet()
            val currentRules = group.excludes.associateBy {
                Pair(getRuleEntityId(it), KnownRuleTypes.EXCLUDE_RULE)
            } + group.rules.associateBy {
                Pair(getRuleEntityId(it), getRuleType(it))
            }

            Utils.diffChanges(existingRules, currentRules.keys).let { (added, deleted, unchanged) ->
                deleted.forEach {
                    deleteRule(group.entityId, loadRule(it.first, it.second))
                }

                added.map { currentRules[it] }.forEach {
                    val ruleInfo = saveRule(it!!)

                    val pos = if(ruleInfo.second == KnownRuleTypes.EXCLUDE_RULE)
                        group.excludes.indexOf(it)
                    else
                        group.rules.indexOf(it) + group.excludes.size

                    insertEntry(RuleGroupEntry(
                        0,
                        group.entityId,
                        getRuleEntityId(it),
                        ruleInfo.second.ordinal,
                        pos
                    ))
                }

                unchanged.map { currentRules[it] }.forEach {
                    saveRule(it!!)

                    val pos = if(it is ExcludeRule)
                        group.excludes.indexOf(it)
                    else
                        group.rules.indexOf(it) + group.excludes.size
                    updateEntryPos(group.entityId, getRuleEntityId(it), getRuleType(it).ordinal, pos)
                }
            }
        }

        @Transaction
        open fun delete(group: RuleGroup) {
            group.excludes.plus(group.rules).forEach {
                deleteRule(group.entityId, it)
            }

            deleteEntity(group.entityId)
        }

        fun getEntityId(group: RuleGroup): Long {
            return group.entityId
        }

        //endregion

        //region sql
        @Insert
        protected abstract fun insertEntity(entity: RuleGroupEntity): Long

        @Query("DELETE FROM RuleGroupEntity WHERE id = :id;")
        protected abstract fun deleteEntity(id: Long)

        @Query("SELECT * FROM RuleGroupEntity WHERE id = :id;")
        protected abstract fun getEntity(id: Long): RuleGroupEntity

        @Insert
        protected abstract fun insertEntry(entry: RuleGroupEntry): Long

        @Query("DELETE FROM RuleGroupEntry WHERE rulegroup = :group AND rule = :rule AND type = :type;")
        protected abstract fun deleteEntry(group: Long, rule: Long, type: Int)

        @Query("SELECT * FROM RuleGroupEntry WHERE rulegroup = :group;")
        protected abstract fun getEntriesForGroup(group: Long): List<RuleGroupEntry>

        @Query("UPDATE RuleGroupEntry SET pos = :pos WHERE rulegroup = :group AND rule = :rule AND type = :type;")
        protected abstract fun updateEntryPos(group: Long, rule: Long, type: Int, pos: Int)

        //endregion

        //region private helpers
        private fun loadRule(id: Long, type: KnownRuleTypes): Rulelike {
            return when(type) {
                KnownRuleTypes.RULE_GROUP -> this.load(id)
                KnownRuleTypes.EXCLUDE_RULE -> RoomDB.DB_INSTANCE.dplExcludeRuleDao().load(id)
                KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().load(id)
            }
        }

        /**
         * @return Pair<entityId, type>
         */
        private fun saveRule(rule: Rulelike): Pair<Long, KnownRuleTypes> {
            return when(val type = getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> {
                    val rule = rule as RuleGroup
                    this.save(rule)

                    Pair(rule.entityId, type)
                }
                KnownRuleTypes.EXCLUDE_RULE -> {
                    val rule = rule as ExcludeRule
                    val dao = RoomDB.DB_INSTANCE.dplExcludeRuleDao()
                    dao.save(rule)

                    Pair(dao.getEntityId(rule), type)
                }
                KnownRuleTypes.INCLUDE_RULE -> {
                    val rule = rule as IncludeRule
                    val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                    dao.save(rule)

                    Pair(dao.getEntityId(rule), type)
                }
            }
        }

        private fun deleteRule(group: Long, rule: Rulelike) {
            when(val type = getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> {
                    val rule = rule as RuleGroup
                    deleteEntry(group, rule.entityId, type.ordinal)
                    this.delete(rule)
                }
                KnownRuleTypes.EXCLUDE_RULE -> {
                    val rule = rule as ExcludeRule
                    val dao = RoomDB.DB_INSTANCE.dplExcludeRuleDao()
                    deleteEntry(group, dao.getEntityId(rule), KnownRuleTypes.EXCLUDE_RULE.ordinal)
                    dao.delete(rule)
                }
                KnownRuleTypes.INCLUDE_RULE -> {
                    val rule = rule as IncludeRule
                    val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                    deleteEntry(group, dao.getEntityId(rule), type.ordinal)
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
                KnownRuleTypes.EXCLUDE_RULE -> {
                    val dao = RoomDB.DB_INSTANCE.dplExcludeRuleDao()
                    deleteEntry(group, ruleId, ruleType.ordinal)
                    dao.delete(ruleId)
                }
                KnownRuleTypes.INCLUDE_RULE -> {
                    val dao = RoomDB.DB_INSTANCE.dplIncludeRuleDao()
                    deleteEntry(group, ruleId, ruleType.ordinal)
                    dao.delete(ruleId)
                }
            }
        }

        private fun getRuleType(rule: Rulelike) = when(rule) {
            is RuleGroup -> KnownRuleTypes.RULE_GROUP
            is ExcludeRule -> KnownRuleTypes.EXCLUDE_RULE
            is IncludeRule -> KnownRuleTypes.INCLUDE_RULE
            else -> throw IllegalArgumentException("unsupported type")
        }

        private fun getRuleEntityId(rule: Rulelike): Long {
            return when(getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> (rule as RuleGroup).entityId
                KnownRuleTypes.EXCLUDE_RULE -> RoomDB.DB_INSTANCE.dplExcludeRuleDao().getEntityId(rule as ExcludeRule)
                KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().getEntityId(rule as IncludeRule)

            }
        }

        //endregion
    }
}

//region entities
@Entity
internal data class RuleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = RuleGroupEntity::class,
            parentColumns = ["id"], childColumns = ["rulegroup"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(
            value = ["rulegroup", "rule", "type"],
            unique = true
        )
    ]
)
internal data class RuleGroupEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "rulegroup", index = true) val ruleGroup: Long,
    @ColumnInfo(name = "rule") val rule: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "pos") val pos: Int
)
//endregion
