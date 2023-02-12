package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

private const val LOG_TAG = "ID3TagsRule"

internal class ID3TagsRule private constructor(
    override var share: Rule.Share,
    override val isOriginal: Boolean,
    private val entityId: Long
) : Rule<ID3TagsRule>{

    private val tagValues = HashSet<String>()
    private val tagValuesRO = Collections.unmodifiableSet(tagValues)

    var tagType: String = ""
        set(value) {
            field = value
            // reset values because they may not fit to the new type anymore
            tagValues.clear()
        }

    fun getTagValues(): Set<String> {
        return tagValuesRO
    }

    fun addTagValue(value: String) {
        tagValues.add(value)
    }

    fun removeTagValue(value: String) {
        tagValues.remove(value)
    }

    override fun generateItems(amount: Int, exclude: Set<MediaFile>): List<MediaFile> {
        if(tagType.isEmpty()) {
            Log.w(LOG_TAG, "no tag-type set; skipping")
            return emptyList()
        }

        val tagsDao = RoomDB.DB_INSTANCE.id3TagDao()
        return getTagValues().flatMap {
            tagsDao.getFilesWithTag(tagType, it)
        }.toSet().minus(exclude).shuffled().takeOrAll(amount)
    }

    override fun copy(): ID3TagsRule {
        return ID3TagsRule(share.copy(), false, entityId).apply {
            tagType = this@ID3TagsRule.tagType
            tagValues.addAll(this@ID3TagsRule.tagValues)
        }
    }

    override fun applyFrom(other: ID3TagsRule) {
        tagType = other.tagType
        tagValues.addAll(other.tagValues)
        share = other.share.copy()
    }

    override fun equals(other: Any?): Boolean {
        if(other !is ID3TagsRule)
            return false

        return this.tagType == other.tagType
                && this.getTagValues() == other.getTagValues()
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, tagType, getTagValues(), share)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        share.writeToParcel(dest, flags)
        dest.writeLong(entityId)
        dest.writeString(tagType)
        dest.writeStringList(tagValues.toList())
    }

    companion object CREATOR : Parcelable.Creator<ID3TagsRule> {

        override fun createFromParcel(parcel: Parcel): ID3TagsRule {
            return ID3TagsRule(
                Rule.Share.CREATOR.createFromParcel(parcel),
                false,
                parcel.readLong()
            ).apply {
                tagType = parcel.readString()!!
                val values = ArrayList<String>()
                parcel.readStringList(values)
                tagValues.addAll(values)
            }
        }

        override fun newArray(size: Int): Array<ID3TagsRule?> {
            return arrayOfNulls(size)
        }
    }

    @Dao
    internal abstract class ID3TagsRuleDao {

        //region public methods
        @Transaction
        open fun create(initialShare: Rule.Share): ID3TagsRule {
            return insertEntity(ID3TagsRuleEntity(0, initialShare, "")).let {
                ID3TagsRule(initialShare, true, it)
            }
        }

        fun load(id: Long): ID3TagsRule {
            val rule = findEntityWithId(id).let {
                ID3TagsRule(it.share, true, it.id).apply {
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
            updateEntity(ID3TagsRuleEntity(rule.entityId, rule.share, rule.tagType))

            Utils.diffChanges(
                findEntriesForRule(rule.entityId).map { it.value }.toSet(),
                rule.getTagValues()
            ).let { (added, removed, _) ->
                added.forEach {
                    insertEntry(ID3TagsRuleEntry(0, rule.entityId, it))
                }

                removed.forEach {
                    deleteEntry(rule.entityId, it)
                }
            }
        }

        fun delete(rule: ID3TagsRule) {
            delete(rule.entityId)
        }

        @Transaction
        open fun delete(id: Long) {
            deleteAllEntriesForRule(id)
            deleteEntity(id)
        }

        fun getEntityId(rule: ID3TagsRule): Long {
            return rule.entityId
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
}

@Entity
internal data class ID3TagsRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share,
    val tagType: String
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ID3TagsRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["rule", "value"], unique = true)
    ]
)
internal data class ID3TagsRuleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val value: String
)
