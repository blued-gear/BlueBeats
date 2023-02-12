package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.*
import kotlin.NoSuchElementException

/*TODO
    - all rules in a group should be negateable -> handle them as excludes (before share trimming)
        -> rules do not need to take an amount for generation
        -> (alternatively make an union of all excludes an give it as predicate to the generate-method (and keep the amount))
    - group get a logic-mode: all entries are either ANDed or ORed
        -> in AND-mode shares are ignored
 */

internal class RuleGroup private constructor(
    private val entityId: Long,
    override var share: Rule.Share,
    rules: List<Pair<Rule, Boolean>> = emptyList(),
    excludes: List<ExcludeRule> = emptyList()
) : Rule {

    private val rules: MutableList<Pair<Rule, Boolean>> = ArrayList(rules)
    private val rulesRO: List<Pair<Rule, Boolean>> by lazy {
        Collections.unmodifiableList(this.rules)
    }
    private val excludes = ArrayList(excludes)
    private val excludesRO: List<ExcludeRule> by lazy {
        Collections.unmodifiableList(this.excludes)
    }

    override fun generateItems(amount: Int, exclude: Set<MediaFile>): List<MediaFile> {
        val (positiveRules, negativeRules) = getRules().partition { it.second }.let {
            Pair(
                it.first.map { it.first },
                it.second.map { it.first }
            )
        }
        val (relativeRules, absoluteRules) = positiveRules.partition { it.share.isRelative }

        val excludeAcc = exclude + negativeRules.flatMap { it.generateItems(-1, emptySet()) }

        val absoluteItems = absoluteRules.flatMap {
            it.generateItems(it.share.value.toInt(), excludeAcc)
        }.distinct()

        val relativeAmount = amount - absoluteItems.size
        val relativeItems = relativeRules.flatMap {
            it.generateItems((relativeAmount * it.share.value).toInt(), excludeAcc)
        }.distinct()

        return absoluteItems.plus(relativeItems).takeOrAll(amount)
    }

    /**
     * @return List<Pair<rule, negate>>
     */
    fun getRules(): List<Pair<Rule, Boolean>> {
        return rulesRO
    }

    fun addRule(rule: Rule, negate: Boolean = false) {
        rules.add(Pair(rule, negate))
    }

    fun setRuleNegated(rule: Rule, negated: Boolean) {
        val idx = rules.indexOfFirst { it.first == rule }
        if(idx == -1)
            throw NoSuchElementException("rule not found")
        rules.set(idx, Pair(rule, negated))
    }

    fun removeRule(rule: Rule) {
        rules.removeIf { it.first == rule }
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

        return this.getRules() == other.getRules()
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
            INCLUDE_RULE,
            USERTAGS_RULE
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
                .map { Pair(loadRule(it.rule, KnownRuleTypes.values()[it.type]), it.negated) }
                .partition { it.first is ExcludeRule }

            return RuleGroup(
                entity.id, entity.share,
                ruleEntries.map { Pair(it.first as Rule, it.second) },
                excludeEntries.map { it.first as ExcludeRule }
            )
        }

        @Transaction
        open fun save(group: RuleGroup) {
            TODO("fix for this is deferred as mor changes are to come")

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
            group.excludes.plus(group.rules.map { it.first }).forEach {
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
                KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().load(id)
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
                KnownRuleTypes.USERTAGS_RULE -> {
                    val rule = rule as UsertagsRule
                    val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
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
                KnownRuleTypes.USERTAGS_RULE -> {
                    val rule = rule as UsertagsRule
                    val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
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
                KnownRuleTypes.USERTAGS_RULE -> {
                    val dao = RoomDB.DB_INSTANCE.dplUsertagsRuleDao()
                    deleteEntry(group, ruleId, ruleType.ordinal)
                    dao.delete(ruleId)
                }
            }
        }

        private fun getRuleType(rule: Rulelike) = when(rule) {
            is RuleGroup -> KnownRuleTypes.RULE_GROUP
            is ExcludeRule -> KnownRuleTypes.EXCLUDE_RULE
            is IncludeRule -> KnownRuleTypes.INCLUDE_RULE
            is UsertagsRule -> KnownRuleTypes.USERTAGS_RULE
            else -> throw IllegalArgumentException("unsupported type")
        }

        private fun getRuleEntityId(rule: Rulelike): Long {
            return when(getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> (rule as RuleGroup).entityId
                KnownRuleTypes.EXCLUDE_RULE -> RoomDB.DB_INSTANCE.dplExcludeRuleDao().getEntityId(rule as ExcludeRule)
                KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().getEntityId(rule as IncludeRule)
                KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().getEntityId(rule as UsertagsRule)
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
    @ColumnInfo(name = "pos") val pos: Int,
    @ColumnInfo(name = "negated") val negated: Boolean
)
//endregion
