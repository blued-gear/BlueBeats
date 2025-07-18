package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class RuleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: ShareEmbed,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "andMode") val andMode: Boolean,
)
