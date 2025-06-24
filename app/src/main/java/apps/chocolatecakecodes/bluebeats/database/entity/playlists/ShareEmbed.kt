package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share

internal data class ShareEmbed(val value: Float, val isRelative: Boolean) {

    constructor(from: Share) : this(from.value, from.isRelative)

    fun toShare(): Share {
        return Share(value, isRelative)
    }

}
