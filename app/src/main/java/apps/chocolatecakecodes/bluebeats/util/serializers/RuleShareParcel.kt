package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule.Share
import apps.chocolatecakecodes.bluebeats.util.Utils

class RuleShareParcel(
    val content: Share
) : Parcelable {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(content.value)
        Utils.parcelWriteBoolean(dest, content.isRelative)
    }

    companion object CREATOR : Parcelable.Creator<RuleShareParcel> {

        override fun createFromParcel(parcel: Parcel): RuleShareParcel {
            return RuleShareParcel(
                Share(
                    parcel.readFloat(),
                    Utils.parcelReadBoolean(parcel)
                )
            )
        }

        override fun newArray(size: Int): Array<RuleShareParcel?> {
            return arrayOfNulls(size)
        }
    }
}
