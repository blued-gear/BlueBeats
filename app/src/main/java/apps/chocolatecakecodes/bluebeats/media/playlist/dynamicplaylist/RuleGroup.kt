package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.castTo
import apps.chocolatecakecodes.bluebeats.util.removeIfSingle
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.*
import kotlin.collections.ArrayList

internal class RuleGroup private constructor(
    private val entityId: Long,
    override val isOriginal: Boolean,
    override var share: Rule.Share,
    var combineWithAnd: Boolean = false,
    rules: List<Pair<GenericRule, Boolean>> = emptyList()
) : Rule<RuleGroup> {

    private val rules: MutableList<Pair<GenericRule, Boolean>> = ArrayList(rules)
    private val rulesRO: List<Pair<GenericRule, Boolean>> by lazy {
        Collections.unmodifiableList(this.rules)
    }
    private val graveyard: MutableList<GenericRule> = ArrayList()

    override fun generateItems(amount: Int, exclude: Set<MediaFile>): List<MediaFile> {
        val (negativeRules, positiveRules) = getRules().partition { it.second }.let {
            Pair(
                it.first.map { it.first },
                it.second.map { it.first }
            )
        }
        val (relativeRules, evenRules, absoluteRules) = positiveRules.partition {
            it.share.isRelative
        }.let { (relativeRules, absoluteRules) ->
            relativeRules.partition {
                it.share.modeRelative()
            }.let {
                Triple(it.first, it.second, absoluteRules)
            }
        }

        val excludeAcc = exclude + negativeRules.flatMap { it.generateItems(-1, emptySet()) }

        val absoluteItems = absoluteRules.map {
            val localAmount = if(amount >= 0 && !combineWithAnd) it.share.value.toInt() else -1
            it.generateItems(localAmount, excludeAcc)
        }

        val relativeAmount = let {
            if(amount >= 0 && !combineWithAnd){
                (amount - absoluteItems.sumOf { it.size }).coerceAtLeast(0)
            } else {
                -1
            }
        }
        val relativeItems = relativeRules.map {
            val localAmount = if(amount >= 0 && !combineWithAnd) (relativeAmount * it.share.value).toInt() else -1
            it.generateItems(localAmount, excludeAcc)
        }

        val evenAmount = if(amount >= 0 && !combineWithAnd)
                (((1.0 - relativeRules.sumOf { it.share.value.toDouble() }) / evenRules.size) * relativeAmount).toInt()
            else
                -1
        val evenItems = evenRules.map {
            it.generateItems(evenAmount, excludeAcc)
        }

        return let {
            if(combineWithAnd){
                (absoluteItems + relativeItems + evenItems).map {
                    it.toSet()
                }.reduceOrNull { acc, cur ->
                    acc.intersect(cur)
                } ?: emptySet()
            } else {
                val absoluteCombined = LinkedHashSet<MediaFile>()
                absoluteItems.forEach { absoluteCombined.addAll(it) }

                val relativeCombined = LinkedHashSet<MediaFile>()
                relativeItems.forEach { relativeCombined.addAll(it) }
                evenItems.forEach { relativeCombined.addAll(it) }

                absoluteCombined + relativeCombined.shuffled()
            }
        }.takeOrAll(amount)
    }

    /**
     * @return List<Pair<rule, negate>>
     */
    fun getRules(): List<Pair<GenericRule, Boolean>> {
        return rulesRO
    }

    fun addRule(rule: GenericRule, negate: Boolean = false) {
        rules.add(Pair(rule, negate))
    }

    fun getRuleNegated(rule: GenericRule): Boolean? {
        return getRules().find {
            it.first == rule
        }?.second
    }

    fun setRuleNegated(rule: GenericRule, negated: Boolean) {
        val idx = rules.indexOfFirst { it.first == rule }
        if(idx == -1)
            throw NoSuchElementException("rule not found")
        rules.set(idx, Pair(rule, negated))
    }

    fun removeRule(rule: GenericRule) {
        rules.removeIfSingle { it.first == rule }?.let {
            graveyard.add(it.first)
        } ?: throw IllegalArgumentException("given rule was not in this group")
    }

    fun removeRuleAt(idx: Int) {
        rules.removeAt(idx)
    }

    override fun copy(): RuleGroup {
        return RuleGroup(entityId, false, share.copy(), combineWithAnd).apply {
            this@RuleGroup.rules.map {
                Pair(it.first.copy() as GenericRule, it.second)
            }.let {
                rules.addAll(it)
            }
        }
    }

    /**
     * this will make a deep-copy of all subrules (it will create new instances if this and other are both originals)
     * @see Rule.applyFrom
     */
    override fun applyFrom(other: RuleGroup) {
        this.combineWithAnd = other.combineWithAnd
        this.share = other.share.copy()

        data class RuleId(val type: Int, val instance: Long)
        fun ruleId(rule: GenericRule) = RuleId(rule.javaClass.hashCode(), RuleGroupDao.getRuleEntityId(rule))

        val thisRules = this.rules.associateBy { ruleId(it.first) }
        val otherRules = other.rules.associateBy { ruleId(it.first) }
        Utils.diffChanges(thisRules.keys, otherRules.keys).let { (added, deleted, same) ->
            added.map {
                otherRules[it]!!
            }.forEach {  (rule, negate) ->
                if(other.isOriginal)
                    assert(rule.isOriginal) { "original RuleGroups can only contain original subrules" }

                if(this.isOriginal){
                    if(rule.isOriginal && !other.isOriginal){
                        // takeover rule
                        other.takeoverRule(rule)
                        this.addRule(rule, negate)
                    } else {
                        // make new instance
                        when(rule.javaClass) {
                            RuleGroup::class.java -> RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(rule.share)
                            IncludeRule::class.java -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(rule.share)
                            UsertagsRule::class.java -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().createNew(rule.share)
                            else -> throw IllegalArgumentException("unsupported type")
                        }.castTo<Rule<in GenericRule>>().apply {
                            this.applyFrom(rule)
                        }.let {
                            this.addRule(it, negate)
                        }
                    }
                } else {
                    // make (another) copy
                    this.addRule(rule.copy() as GenericRule, negate)
                }
            }

            deleted.map {
                thisRules[it]!!
            }.forEach { (rule, _) ->
                if(this.isOriginal)
                    assert(rule.isOriginal) { "original RuleGroups can only contain original subrules" }

                this.removeRule(rule)
            }

            same.map { id ->
                otherRules[id]!!.let {
                    Triple(it.first, it.second, thisRules[id]!!.first)
                }
            }.forEach { (otherRule, negate, thisRule) ->
                if(this.isOriginal)
                    assert(thisRule.isOriginal) { "original RuleGroups can only contain original subrules" }
                if(other.isOriginal)
                    assert(otherRule.isOriginal) { "original RuleGroups can only contain original subrules" }

                thisRule.castTo<Rule<in GenericRule>>().applyFrom(otherRule)

                this.setRuleNegated(thisRule, negate)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is RuleGroup)
            return false

        return this.getRules() == other.getRules()
                && this.combineWithAnd == other.combineWithAnd
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, getRules(), combineWithAnd, share)
    }

    /**
     * replace the rule of this group with a copy (this instance must not be an original)
     */
    private fun takeoverRule(rule: GenericRule) {
        if(isOriginal)
            throw IllegalStateException("can not takeover rule from original")

        this.rules.removeIfSingle { it.first == rule }?.let {
            addRule(it.first.copy() as GenericRule, it.second)
        } ?: throw IllegalArgumentException("rule was not in this group")
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(entityId)
        share.writeToParcel(dest, flags)
        Utils.parcelWriteBoolean(dest, combineWithAnd)

        dest.writeInt(rules.size)
        rules.forEach {
            dest.writeParcelable(it.first, flags)
            Utils.parcelWriteBoolean(dest, it.second)
        }
    }

    companion object CREATOR : Parcelable.Creator<RuleGroup> {

        override fun createFromParcel(parcel: Parcel): RuleGroup {
            return RuleGroup(
                parcel.readLong(),
                false,
                Rule.Share.CREATOR.createFromParcel(parcel),
                Utils.parcelReadBoolean(parcel)
            ).apply {
                for(i in 0 until parcel.readInt()) {
                    rules.add(Pair(
                        parcel.readParcelable(Rule::class.java.classLoader)!!,
                        Utils.parcelReadBoolean(parcel)
                    ))
                }
            }
        }

        override fun newArray(size: Int): Array<RuleGroup?> {
            return arrayOfNulls(size)
        }
    }

    @Suppress("NAME_SHADOWING")
    @Dao
    internal abstract class RuleGroupDao {

        private enum class KnownRuleTypes {
            RULE_GROUP,
            INCLUDE_RULE,
            USERTAGS_RULE,
            ID3TAGS_RULE
        }

        companion object {
            private fun getRuleType(rule: GenericRule) = when(rule) {
                is RuleGroup -> KnownRuleTypes.RULE_GROUP
                is IncludeRule -> KnownRuleTypes.INCLUDE_RULE
                is UsertagsRule -> KnownRuleTypes.USERTAGS_RULE
                is ID3TagsRule -> KnownRuleTypes.ID3TAGS_RULE
                else -> throw IllegalArgumentException("unsupported type")
            }

            fun getRuleEntityId(rule: GenericRule): Long {
                return when(getRuleType(rule)) {
                    KnownRuleTypes.RULE_GROUP -> (rule as RuleGroup).entityId
                    KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().getEntityId(rule as IncludeRule)
                    KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().getEntityId(rule as UsertagsRule)
                    KnownRuleTypes.ID3TAGS_RULE -> RoomDB.DB_INSTANCE.dplID3TagsRuleDao().getEntityId(rule as ID3TagsRule)
                }
            }
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
                entity.id, true, entity.share, entity.andMode,
                ruleEntries.map { Pair(it.first, it.second) },
            )
        }

        @Transaction
        open fun save(group: RuleGroup) {
            if(!group.isOriginal)
                throw IllegalArgumentException("only original rules may be saved to DB")

            val existingRules = getEntriesForGroup(group.entityId).map {
                Pair(it.rule, KnownRuleTypes.values()[it.type])
            }.toSet()
            val currentRules = group.getRules().associateBy {
                Pair(getRuleEntityId(it.first), getRuleType(it.first))
            }

            Utils.diffChanges(existingRules, currentRules.keys).let { (added, deleted, existing) ->
                group.graveyard.map {
                    Pair(getRuleEntityId(it), getRuleType(it))
                }.toSet().plus(deleted).forEach {
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
        private fun loadRule(id: Long, type: KnownRuleTypes): GenericRule {
            return when(type) {
                KnownRuleTypes.RULE_GROUP -> this.load(id)
                KnownRuleTypes.INCLUDE_RULE -> RoomDB.DB_INSTANCE.dplIncludeRuleDao().load(id)
                KnownRuleTypes.USERTAGS_RULE -> RoomDB.DB_INSTANCE.dplUsertagsRuleDao().load(id)
                KnownRuleTypes.ID3TAGS_RULE -> RoomDB.DB_INSTANCE.dplID3TagsRuleDao().load(id)
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
            }
        }

        private fun deleteRule(group: Long, rule: GenericRule) {
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
                KnownRuleTypes.ID3TAGS_RULE -> {
                    val rule = rule as ID3TagsRule
                    val dao = RoomDB.DB_INSTANCE.dplID3TagsRuleDao()
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
                KnownRuleTypes.ID3TAGS_RULE -> {
                    val dao = RoomDB.DB_INSTANCE.dplID3TagsRuleDao()
                    deleteEntry(group, ruleId, ruleType.ordinal)
                    dao.delete(ruleId)
                }
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
