package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class ID3TagsRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: ShareEmbed,
    val name: String,
    val tagType: String,
)
