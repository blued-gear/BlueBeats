package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.database.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.TimeSpanItem
import java.util.Objects

internal class TimeSpanRule private constructor(
    private val entityId: Long,
    override val isOriginal: Boolean,
    var file: MediaFile,
    var startMs: Long,
    var endMs: Long,
    var description: String,
    initialShare: Rule.Share
): Rule<TimeSpanRule> {

    override var share = initialShare

    override fun generateItems(amount: Int, exclude: Set<PlaylistItem>): List<PlaylistItem> {
        if(amount == 0)
            return emptyList()

        if(file === MediaNode.INVALID_FILE)
            return emptyList()

        val item = TimeSpanItem(file, startMs, endMs)

        if(exclude.contains(item))
            return emptyList()

        return listOf(item)
    }

    override fun copy(): TimeSpanRule {
        return TimeSpanRule(entityId, false, file, startMs, endMs, description, share.copy())
    }

    override fun applyFrom(other: TimeSpanRule) {
        file = other.file
        startMs = other.startMs
        endMs = other.endMs
        description = other.description
        share = other.share.copy()
    }

    override fun equals(other: Any?): Boolean {
        if(other !is TimeSpanRule)
            return false

        return this.file.shallowEquals(other.file)
                && this.startMs == other.startMs
                && this.endMs == other.endMs
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, file, startMs, endMs, share)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(entityId)
        share.writeToParcel(dest, flags)

        dest.writeLong(file.entityId)
        dest.writeLong(startMs)
        dest.writeLong(endMs)
        dest.writeString(description)
    }

    companion object CREATOR : Parcelable.Creator<TimeSpanRule> {

        override fun createFromParcel(source: Parcel): TimeSpanRule {
            return TimeSpanRule(
                entityId = source.readLong(),
                isOriginal = false,
                initialShare = Rule.Share.CREATOR.createFromParcel(source),
                file = source.readLong().let { fileId ->
                    if(fileId == MediaNode.INVALID_FILE.entityId)
                        MediaNode.INVALID_FILE
                    else
                        RoomDB.DB_INSTANCE.mediaFileDao().getForId(fileId)
                } ,
                startMs = source.readLong(),
                endMs = source.readLong(),
                description = source.readString()!!
            )
        }

        override fun newArray(size: Int): Array<TimeSpanRule?> {
            return arrayOfNulls(size)
        }
    }

    @Dao
    internal abstract class TimeSpanRuleDao {

        private val fileDao: MediaFileDAO by lazy {
            RoomDB.DB_INSTANCE.mediaFileDao()
        }

        //region api
        @Transaction
        open fun createNew(initialShare: Rule.Share): TimeSpanRule {
            val id = insertEntity(TimeSpanRuleEntity(0, initialShare, null, 0, 0, ""))
            return TimeSpanRule(id, true, MediaNode.INVALID_FILE, 0, 0, "", initialShare)
        }

        fun load(id: Long): TimeSpanRule {
            val entity = getEntity(id)

            val file = entity.file?.let {
                fileDao.getForId(it)
            } ?: MediaNode.INVALID_FILE

            return TimeSpanRule(id, true, file,
                entity.startMs, entity.endMs,
                entity.desc, entity.share)
        }

        @Transaction
        open fun save(rule: TimeSpanRule) {
            val fileId = if(rule.file === MediaNode.INVALID_FILE)
                null
            else
                rule.file.entityId

            updateEntity(TimeSpanRuleEntity(rule.entityId, rule.share, fileId,
                rule.startMs, rule.endMs, rule.description))
        }

        fun delete(rule: TimeSpanRule) {
            delete(rule.entityId)
        }

        @Transaction
        open fun delete(id: Long) {
            deleteEntity(id)
        }

        fun getEntityId(rule: TimeSpanRule): Long {
            return rule.entityId
        }
        //endregion

        //region sql
        @Query("SELECT * FROM TimeSpanRuleEntity WHERE id = :id;")
        protected abstract fun getEntity(id: Long): TimeSpanRuleEntity

        @Insert
        protected abstract fun insertEntity(entity: TimeSpanRuleEntity): Long

        @Update
        protected abstract fun updateEntity(entity: TimeSpanRuleEntity)

        @Query("DELETE FROM TimeSpanRuleEntity WHERE id = :id;")
        protected abstract fun deleteEntity(id: Long)
        //endregion
    }
}

//region entities
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["file"])
    ]
)
internal data class TimeSpanRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share,
    val file: Long?,
    val startMs: Long,
    val endMs: Long,
    val desc: String
)
//endregion
