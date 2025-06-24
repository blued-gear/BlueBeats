package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule

class ID3TagsRuleParcel(
    override val content: ID3TagsRule
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        RuleShareParcel(content.share).writeToParcel(dest, flags)
        dest.writeLong(content.id)
        dest.writeString(content.name)
        dest.writeString(content.tagType)
        dest.writeStringList(content.getTagValues().toList())
    }

    companion object CREATOR : Parcelable.Creator<ID3TagsRuleParcel> {

        override fun createFromParcel(parcel: Parcel): ID3TagsRuleParcel {
            return ID3TagsRule(
                RuleShareParcel.createFromParcel(parcel).content,
                false,
                parcel.readLong(),
                parcel.readString()!!,
            ).apply {
                tagType = parcel.readString()!!

                val values = ArrayList<String>()
                parcel.readStringList(values)
                values.forEach { this.addTagValue(it) }
            }.let { ID3TagsRuleParcel(it) }
        }

        override fun newArray(size: Int): Array<ID3TagsRuleParcel?> {
            return arrayOfNulls(size)
        }
    }
}