package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = RuleGroupEntity::class,
            parentColumns = ["id"], childColumns = ["ruleRoot"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ruleRoot"], unique = true)
    ]
)
internal data class DynamicPlaylistEntity(
    @PrimaryKey(autoGenerate = false) val id: Long,
    val ruleRoot: Long,
    val iterationSize: Int
)
