package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ID3TagsRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["rule", "value"], unique = true)
    ]
)
internal data class ID3TagsRuleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val value: String
)
