package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.*

internal class RuleGroup private constructor(
    private val entityId: Long,
    override var share: Rule.Share,
    var combineWithAnd: Boolean = false,
    rules: List<Pair<Rule, Boolean>> = emptyList()
) : Rule {

    private val rules: MutableList<Pair<Rule, Boolean>> = ArrayList(rules)
    private val rulesRO: List<Pair<Rule, Boolean>> by lazy {
        Collections.unmodifiableList(this.rules)
    }

    override fun generateItems(amount: Int, exclude: Set<MediaFile>): List<MediaFile> {
        val (negativeRules, positiveRules) = getRules().partition { it.second }.let {
            Pair(
                it.first.map { it.first },
                it.second.map { it.first }
            )
        }
        val (relativeRules, absoluteRules) = positiveRules.partition { it.share.isRelative }

        val excludeAcc = exclude + negativeRules.flatMap { it.generateItems(-1, emptySet()) }

        val absoluteItems = absoluteRules.map {
            val localAmount = if(amount >= 0 && !combineWithAnd) it.share.value.toInt() else -1
            it.generateItems(localAmount, excludeAcc)
        }

        val relativeAmount = let {
            if(amount >= 0 && !combineWithAnd){
                amount - absoluteItems.sumOf { it.size }
            } else {
                -1
            }
        }
        val relativeItems = relativeRules.map {
            val localAmount = if(amount >= 0 && !combineWithAnd) (relativeAmount * it.share.value).toInt() else -1
            it.generateItems(localAmount, excludeAcc)
        }

        return let {
            if(combineWithAnd){
                absoluteItems
                    .map { it.toSet() }
                    .reduceOrNull { acc, cur ->
                        acc.intersect(cur)
                    }?.let { absoluteIntersect ->
                        relativeItems
                            .map { it.toSet() }
                            .fold(absoluteIntersect) { acc, cur ->
                                acc.intersect(cur)
                            }
                    } ?: emptySet()
            } else {
                val absoluteCombined = LinkedHashSet<MediaFile>()
                absoluteItems.forEach { absoluteCombined.addAll(it) }

                val relativeCombined = LinkedHashSet<MediaFile>()
                relativeItems.forEach { relativeCombined.addAll(it) }

                absoluteCombined + relativeCombined.shuffled()
            }
        }.takeOrAll(amount)
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

    fun getRuleNegated(rule: Rule): Boolean? {
        return getRules().find {
            it.first == rule
        }?.second
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

    override fun equals(other: Any?): Boolean {
        if(other !is RuleGroup)
            return false

        return this.getRules() == other.getRules()
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, getRules())
    }

    @Suppress("NAME_SHADOWING")
    @Dao
    internal abstract class RuleGroupDao {

        private enum class KnownRuleTypes {
            RULE_GROUP,
            INCLUDE_RULE,
            USERTAGS_RULE
        }

        //region api
        @Transaction
        open fun createNew(initialShare: Rule.Share): RuleGroup {
            return load(insertEntity(RuleGroupEntity(0, initialShare, false)))
        }

        fun load(id: Long): RuleGroup {
            val entity = getEntity(id)
            val ruleEntries = getEntriesForGroup(id)
                .sortedBy { it.pos }
                .map { Pair(loadRule(it.rule, KnownRuleTypes.values()[it.type]), it.negated) }

            return RuleGroup(
                entity.id, entity.share, entity.andMode,
                ruleEntries.map { Pair(it.first, it.second) },
            )
        }

        @Transaction
        open fun save(group: RuleGroup) {
            val existingRules = getEntriesForGroup(group.entityId).map {
                Pair(it.rule, KnownRuleTypes.values()[it.type])
            }.toSet()
            val currentRules = group.getRules().associateBy {
                Pair(getRuleEntityId(it.first), getRuleType(it.first))
            }

            Utils.diffChanges(existingRules, currentRules.keys).let { (added, deleted, existing) ->
                deleted.forEach {
                    deleteRule(group.entityId, it.first, it.second)
                }

                added.forEach {
                    val (id, type)  = it
                    val ruleWithNegated = currentRules[it]!!
                    val (rule, negated) = ruleWithNegated
                    val pos = group.getRules().indexOf(ruleWithNegated)

                    saveRule(rule)
                    insertEntry(RuleGroupEntry(
                        0,
                        group.entityId,
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
                    updateEntryPos(group.entityId, id, type.ordinal, pos, negated)
                }
            }

            updateEntity(RuleGroupEntity(group.entityId, group.share, group.combineWithAnd))
        }

        @Transaction
        open fun delete(group: RuleGroup) {
            group.getRules().map { it.first }.forEach {
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

        @Update
        protected abstract fun updateEntity(entity: RuleGroupEntity)

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

        @Query("UPDATE RuleGroupEntry SET pos = :pos, negated = :negated WHERE rulegroup = :group AND rule = :rule AND type = :type;")
        protected abstract fun updateEntryPos(group: Long, rule: Long, type: Int, pos: Int, negated: Boolean)

        //endregion

        //region private helpers
        private fun loadRule(id: Long, type: KnownRuleTypes): Rule {
            return when(type) {
                KnownRuleTypes.RULE_GROUP -> this.load(id)
                KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().load(id)
                KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().load(id)
            }
        }

        /**
         * @return Pair<entityId, type>
         */
        private fun saveRule(rule: Rule) {
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
            }
        }

        private fun deleteRule(group: Long, rule: Rule) {
            when(val type = getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> {
                    val rule = rule as RuleGroup
                    deleteEntry(group, rule.entityId, type.ordinal)
                    this.delete(rule)
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

        private fun getRuleType(rule: Rule) = when(rule) {
            is RuleGroup -> KnownRuleTypes.RULE_GROUP
            is IncludeRule -> KnownRuleTypes.INCLUDE_RULE
            is UsertagsRule -> KnownRuleTypes.USERTAGS_RULE
            else -> throw IllegalArgumentException("unsupported type")
        }

        private fun getRuleEntityId(rule: Rule): Long {
            return when(getRuleType(rule)) {
                KnownRuleTypes.RULE_GROUP -> (rule as RuleGroup).entityId
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
    @Embedded(prefix = "share_") val share: Rule.Share,
    @ColumnInfo(name = "andMode") val andMode: Boolean
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
