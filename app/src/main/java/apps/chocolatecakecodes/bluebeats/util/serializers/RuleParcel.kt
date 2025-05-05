package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule

sealed interface RuleParcel : Parcelable {
    val content: GenericRule
}
