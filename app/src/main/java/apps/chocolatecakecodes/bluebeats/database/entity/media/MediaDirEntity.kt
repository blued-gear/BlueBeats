package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class,
        parentColumns = ["id"], childColumns = ["parent"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaDirEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long
)
