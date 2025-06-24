package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class IncludeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: ShareEmbed,
    val name: String,
)
