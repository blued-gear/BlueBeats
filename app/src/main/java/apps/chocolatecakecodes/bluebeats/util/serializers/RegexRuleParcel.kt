package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule

class RegexRuleParcel(
    override val content: RegexRule
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(content.id)
        RuleShareParcel(content.share).writeToParcel(dest, flags)
        dest.writeInt(content.attribute.ordinal)
        dest.writeString(content.regex)
    }

    companion object CREATOR : Parcelable.Creator<RegexRuleParcel> {
        override fun createFromParcel(parcel: Parcel): RegexRuleParcel {
            return RegexRule(
                id = parcel.readLong(),
                initialShare = RuleShareParcel.createFromParcel(parcel).content,
                attribute = RegexRule.Attribute.entries[parcel.readInt()],
                regex = parcel.readString()!!,
                isOriginal = false
            ).let { RegexRuleParcel(it) }
        }

        override fun newArray(size: Int): Array<RegexRuleParcel?> {
            return arrayOfNulls(size)
        }
    }
}
