package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaDirEntity

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = IncludeRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaDirEntity::class,
            parentColumns = ["id"], childColumns = ["dir"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["rule", "dir"], unique = true),
        Index(value = ["dir"])
    ]
)
internal data class IncludeRuleDirEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val dir: Long,
    val deep: Boolean
)
