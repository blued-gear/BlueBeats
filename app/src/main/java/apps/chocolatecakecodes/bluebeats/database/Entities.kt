package apps.chocolatecakecodes.bluebeats.database

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.taglib.TagFields

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class,
        parentColumns = ["id"], childColumns = ["parent"],
        onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaDirEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long
)

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class,
        parentColumns = ["id"], childColumns = ["parent"],
        onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long,
    @ColumnInfo(name = "type", index = true) var type: MediaFile.Type,

    @ColumnInfo(name = "chapters", index = false)
    var chaptersJson: String?// store them as JSON because this is the easiest way to store lists
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["file", "type"], unique = true)
    ]
)
internal data class ID3TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val file: Long,
    val type: String,
    val value: String
)

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
internal data class UserTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name") val name: String
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = UserTagEntity::class,
            parentColumns = ["id"], childColumns = ["tag"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT),
        ForeignKey(entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["tag", "file"], unique = true)]
)
internal data class UserTagRelation(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "tag") val tag: Long,
    @ColumnInfo(name = "file", index = true) val file: Long,
    @ColumnInfo(name = "pos") val pos: Int
)