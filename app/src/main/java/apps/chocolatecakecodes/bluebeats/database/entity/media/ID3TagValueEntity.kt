package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
