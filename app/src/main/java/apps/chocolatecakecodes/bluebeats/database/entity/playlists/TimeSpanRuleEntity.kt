package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaFileEntity

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
    @Embedded(prefix = "share_") val share: ShareEmbed,
    val name: String,
    val file: Long?,
    val startMs: Long,
    val endMs: Long,
    val desc: String,
)
