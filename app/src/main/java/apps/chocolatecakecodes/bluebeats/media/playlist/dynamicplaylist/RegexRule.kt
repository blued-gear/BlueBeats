package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.util.Objects

internal class RegexRule private constructor(
    private val entityId: Long,
    override val isOriginal: Boolean,
    var attribute: Attribute,
    regex: String,
    initialShare: Rule.Share
) : Rule<RegexRule>{

    enum class Attribute {
        FILENAME,
        FILEPATH,
        TITLE,
        ARTIST,
        USERTAGS
    }

    override var share = initialShare
    var regex: String
        get() = matcher.pattern
        set(value) { matcher = Regex(value) }

    private var matcher = Regex(regex)

    //region public functions
    override fun generateItems(amount: Int, exclude: Set<MediaFile>): List<MediaFile> {
        val fileDao = RoomDB.DB_INSTANCE.mediaFileDao()
        return fileDao.getAllFiles().asSequence().filterNot {
            exclude.contains(it)
        }.map {
            val attr: List<String> = when (attribute) {
                Attribute.FILENAME -> listOf(it.name)
                Attribute.FILEPATH -> listOf(it.path)
                Attribute.TITLE -> listOf(it.mediaTags.title)
                Attribute.ARTIST -> listOf(it.mediaTags.artist)
                Attribute.USERTAGS -> it.userTags
            }.filterNot {
                it.isNullOrEmpty()
            }

            Pair(it, attr)
        }.filterNot {
            it.second.isEmpty()
        }.filter {
            it.second.any {
                matcher.matches(it)
            }
        }.map {
            it.first
        }.shuffled().takeOrAll(amount).toList()
    }

    override fun copy(): RegexRule {
        return RegexRule(
            entityId,
            false,
            attribute,
            regex,
            share.copy()
        )
    }

    override fun equals(other: Any?): Boolean {
        if(other !is RegexRule)
            return false

        return this.attribute == other.attribute
                && this.regex == other.regex
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, attribute, regex)
    }

    override fun applyFrom(other: RegexRule) {
        attribute = other.attribute
        matcher = Regex(other.regex)
        share = other.share.copy()
    }
    //endregion

    //region Parcelable
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(entityId)
        share.writeToParcel(dest, flags)
        dest.writeInt(attribute.ordinal)
        dest.writeString(regex)
    }

    companion object CREATOR : Parcelable.Creator<RegexRule> {
        override fun createFromParcel(parcel: Parcel): RegexRule {
            return RegexRule(
                entityId = parcel.readLong(),
                initialShare = Rule.Share.CREATOR.createFromParcel(parcel),
                attribute = Attribute.values()[parcel.readInt()],
                regex = parcel.readString()!!,
                isOriginal = false
            )
        }

        override fun newArray(size: Int): Array<RegexRule?> {
            return arrayOfNulls(size)
        }
    }
    //endregion

    //region Dao
    @Dao
    internal abstract class RegexRuleDao {

        @Transaction
        open fun createNew(initialShare: Rule.Share): RegexRule {
            val entity = RegexRuleEntity(0, initialShare, Attribute.TITLE, "")
            val id = insertEntity(entity)
            return RegexRule(id, true, entity.attribute, entity.regex, entity.share)
        }

        open fun load(id: Long): RegexRule {
            val entity = getEntity(id)
            return RegexRule(
                entity.id, true,
                entity.attribute, entity.regex,
                entity.share)
        }

        @Transaction
        open fun save(rule: RegexRule) {
            updateEntity(
                RegexRuleEntity(
                rule.entityId,
                rule.share,
                rule.attribute,
                rule.regex
            )
            )
        }

        @Transaction
        open fun delete(rule: RegexRule) {
            delete(rule.entityId)
        }

        @Transaction
        open fun delete(ruleId: Long) {
            deleteEntity(ruleId)
        }

        fun getEntityId(rule: RegexRule): Long {
            return rule.entityId
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
    //endregion
}

//region entities
@Entity
internal data class RegexRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share,
    val attribute: RegexRule.Attribute,
    val regex: String
)
//endregion
