package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule

internal data class ShareEmbed(val value: Float, val isRelative: Boolean) {

    constructor(from: Rule.Share) : this(from.value, from.isRelative)

    fun toShare(): Rule.Share {
        return Rule.Share(value, isRelative)
    }

}
