package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = RuleGroupEntity::class,
            parentColumns = ["id"], childColumns = ["rulegroup"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(
            value = ["rulegroup", "rule", "type"],
            unique = true
        )
    ]
)
internal data class RuleGroupEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "rulegroup", index = true) val ruleGroup: Long,
    @ColumnInfo(name = "rule") val rule: Long,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "pos") val pos: Int,
    @ColumnInfo(name = "negated") val negated: Boolean
)
