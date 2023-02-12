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
    indices = [Index(value = ["str"], unique = true)]
)
internal data class ID3TagTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val str: String
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ID3TagTypeEntity::class,
            parentColumns = ["id"], childColumns = ["type"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["type", "str"], unique = true)]
)
internal data class ID3TagValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val type: Long,
    val str: String
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ID3TagValueEntity::class,
            parentColumns = ["id"], childColumns = ["tag"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tag", "file"], unique = true),
        Index(value = ["file"])
    ]
)
internal data class ID3TagReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val tag: Long,
    val file: Long
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