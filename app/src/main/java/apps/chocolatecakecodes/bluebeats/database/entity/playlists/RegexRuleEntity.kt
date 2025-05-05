package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule

@Entity
internal data class RegexRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: ShareEmbed,
    val attribute: RegexRule.Attribute,
    val regex: String
)
