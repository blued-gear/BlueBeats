package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaFileEntity

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = IncludeRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["rule", "file"], unique = true),
        Index(value = ["file"])
    ]
)
internal data class IncludeRuleFileEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val file: Long
)
