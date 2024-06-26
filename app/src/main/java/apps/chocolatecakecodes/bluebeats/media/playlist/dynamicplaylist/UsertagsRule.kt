package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.*

internal class UsertagsRule private constructor(
    override var share: Rule.Share,
    /** if true returned files will match all tags; if false returned files will match any of the tags */
    var combineWithAnd: Boolean = true,
    override val isOriginal: Boolean,
    private val entityId: Long
) : Rule<UsertagsRule> {

    private val tagsDao = RoomDB.DB_INSTANCE.userTagDao()
    private val tags = HashSet<String>()
    private val tagsRO = Collections.unmodifiableSet(tags)

    fun addTag(tag: String) {
        tags.add(tag)
    }

    fun removeTag(tag: String) {
        tags.remove(tag)
    }

    fun getTags(): Set<String> {
        return tagsRO
    }

    override fun generateItems(amount: Int, exclude: Set<PlaylistItem>): List<PlaylistItem> {
        val excludedFiles = exclude.mapNotNull {
            if(it is MediaFileItem)
                it.file
            else
                null
        }.toSet()

        // and-combine results (all tags must match)
        val tags = getTags()
        return tagsDao.getFilesForTags(tags.toList())
            .minus(excludedFiles)
            .let {
                if (combineWithAnd)
                    combineAnd(it)
                else
                    combineOr(it)
            }
            .map { MediaFileItem(it) }
            .toList()
            .shuffled()
            .takeOrAll(amount)
    }

    override fun copy(): UsertagsRule {
        return UsertagsRule(share.copy(), combineWithAnd, false, entityId).apply {
            tags.addAll(this@UsertagsRule.tags)
        }
    }

    override fun applyFrom(other: UsertagsRule) {
        tags.clear()
        tags.addAll(other.tags)
        combineWithAnd = other.combineWithAnd
        share = other.share.copy()
    }

    override fun equals(other: Any?): Boolean {
        if(other !is UsertagsRule)
            return false

        return this.getTags() == other.getTags()
                && this.combineWithAnd == other.combineWithAnd
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, getTags(), combineWithAnd, share)
    }

    private fun combineAnd(results: Map<MediaFile, List<String>>): Set<MediaFile> {
        return results.filterValues {
            it.containsAll(getTags())
        }.keys
    }

    private fun combineOr(results: Map<MediaFile, List<String>>): Set<MediaFile> {
        // already combined by getFilesForTags()
        return results.keys
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        share.writeToParcel(dest, flags)
        Utils.parcelWriteBoolean(dest, combineWithAnd)
        dest.writeLong(entityId)
        dest.writeStringList(tags.toList())
    }

    companion object CREATOR : Parcelable.Creator<UsertagsRule> {

        override fun createFromParcel(parcel: Parcel): UsertagsRule {
            return UsertagsRule(
                Rule.Share.CREATOR.createFromParcel(parcel),
                Utils.parcelReadBoolean(parcel),
                false,
                parcel.readLong()
            ).apply {
                ArrayList<String>().let {
                    parcel.readStringList(it)
                    tags.addAll(it)
                }
            }
        }

        override fun newArray(size: Int): Array<UsertagsRule?> {
            return arrayOfNulls(size)
        }
    }

    @Dao
    internal abstract class UsertagsRuleDao{

        //region public methods
        @Transaction
        open fun createNew(share: Rule.Share): UsertagsRule {
            val initialAndMode = true
            val id = insertEntity(UsertagsRuleEntity(0, share, initialAndMode))
            return UsertagsRule(share, initialAndMode, true, id)
        }

        fun load(id: Long): UsertagsRule {
            return getEntity(id).let {
                UsertagsRule(it.share, it.andMode, true, it.id)
            }.apply {
                getAllEntriesForRule(entityId).forEach {
                    addTag(it.tag)
                }
            }
        }

        @Transaction
        open fun save(rule: UsertagsRule) {
            if(!rule.isOriginal)
                throw IllegalArgumentException("only original rules may be saved to DB")

            val storedTags = getAllEntriesForRule(rule.entityId).map { it.tag }.toSet()
            Utils.diffChanges(storedTags, rule.getTags()).let { (added, deleted, _) ->
                deleted.forEach {
                    deleteEntry(rule.entityId, it)
                }
                added.forEach {
                    insertEntry(UsertagsRuleEntry(0, rule.entityId, it))
                }
            }

            updateEntity(UsertagsRuleEntity(rule.entityId, rule.share, rule.combineWithAnd))
        }

        fun delete(rule: UsertagsRule) {
            delete(rule.entityId)
        }

        @Transaction
        open fun delete(id: Long) {
            deleteAllEntriesForRule(id)
            deleteEntity(id)
        }

        fun getEntityId(rule: UsertagsRule): Long {
            return rule.entityId
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

        @Query("SELECT * FROM UsertagsRuleEntry WHERE rule = :rule;")
        abstract fun getAllEntriesForRule(rule: Long): List<UsertagsRuleEntry>

        @Query("DELETE FROM UsertagsRuleEntry WHERE rule = :rule;")
        abstract fun deleteAllEntriesForRule(rule: Long)

        @Query("DELETE FROM UsertagsRuleEntity WHERE id = :id;")
        abstract fun deleteEntity(id: Long)

        @Query("DELETE FROM UsertagsRuleEntry WHERE rule = :rule AND tag = :tag;")
        abstract fun deleteEntry(rule: Long, tag: String)
        //endregion
    }
}

@Entity
internal data class UsertagsRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share,
    val andMode: Boolean
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = UsertagsRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["rule"])
    ]
)
internal data class UsertagsRuleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val tag: String
)
