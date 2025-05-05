package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RegexRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.util.Utils

class RuleGroupParcel(
    override val content: RuleGroup
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(content.id)
        RuleShareParcel(content.share).writeToParcel(dest, flags)
        Utils.parcelWriteBoolean(dest, content.combineWithAnd)

        content.getRules().let {
            dest.writeInt(it.size)

            it.forEach {
                when(val rule = it.first) {
                    is ID3TagsRule -> ID3TagsRuleParcel(rule)
                    is IncludeRule -> IncludeRuleParcel(rule)
                    is RegexRule -> RegexRuleParcel(rule)
                    is RuleGroup -> RuleGroupParcel(rule)
                    is TimeSpanRule -> TimeSpanRuleParcel(rule)
                    is UsertagsRule -> UsertagsRuleParcel(rule)
                }.let { dest.writeParcelable(it, flags) }

                Utils.parcelWriteBoolean(dest, it.second)
            }
        }
    }

    companion object CREATOR : Parcelable.Creator<RuleGroupParcel> {

        override fun createFromParcel(parcel: Parcel): RuleGroupParcel {
            return RuleGroup(
                parcel.readLong(),
                false,
                RuleShareParcel.createFromParcel(parcel).content,
                Utils.parcelReadBoolean(parcel)
            ).apply {
                val cl = Rule::class.java.classLoader
                for(i in 0 until parcel.readInt()) {
                    this.addRule(
                        parcel.readParcelable<RuleParcel>(cl)!!.content,
                        Utils.parcelReadBoolean(parcel)
                    )
                }
            }.let { RuleGroupParcel(it) }
        }

        override fun newArray(size: Int): Array<RuleGroupParcel?> {
            return arrayOfNulls(size)
        }
    }
}
