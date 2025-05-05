package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
