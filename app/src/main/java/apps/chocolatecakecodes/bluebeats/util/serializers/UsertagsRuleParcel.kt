package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.util.Utils

class UsertagsRuleParcel(
    override val content: UsertagsRule
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        RuleShareParcel(content.share).writeToParcel(dest, flags)
        dest.writeLong(content.id)
        Utils.parcelWriteBoolean(dest, content.combineWithAnd)
        dest.writeStringList(content.getTags().toList())
    }

    companion object CREATOR : Parcelable.Creator<UsertagsRuleParcel> {

        override fun createFromParcel(parcel: Parcel): UsertagsRuleParcel {
            return UsertagsRule(
                share = RuleShareParcel.createFromParcel(parcel).content,
                id = parcel.readLong(),
                combineWithAnd = Utils.parcelReadBoolean(parcel),
                isOriginal = false,
            ).apply {
                ArrayList<String>().apply {
                    parcel.readStringList(this)
                }.forEach {
                    this.addTag(it)
                }
            }.let { UsertagsRuleParcel(it) }
        }

        override fun newArray(size: Int): Array<UsertagsRuleParcel?> {
            return arrayOfNulls(size)
        }
    }
}
