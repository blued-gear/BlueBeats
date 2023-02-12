package apps.chocolatecakecodes.bluebeats.database

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileEntity

//TODO move other entities here

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
internal data class UserTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name") val name: String
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = UserTagEntity::class, parentColumns = ["id"], childColumns = ["tag"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = MediaFileEntity::class, parentColumns = ["id"], childColumns = ["file"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tag", "file"], unique = true)]
)
internal data class UserTagRelation(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "tag") val tag: Long,
    @ColumnInfo(name = "file", index = true) val file: Long
)