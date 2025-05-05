package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = UsertagsRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["rule"])
    ]
)
internal data class UsertagsRuleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val tag: String
)
