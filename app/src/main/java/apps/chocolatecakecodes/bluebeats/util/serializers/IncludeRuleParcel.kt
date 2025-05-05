package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.Utils

class IncludeRuleParcel(
    override val content: IncludeRule
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(content.id)
        RuleShareParcel(content.share).writeToParcel(dest, flags)

        content.getDirs().let {
            dest.writeInt(it.size)
            it.forEach {
                dest.writeLong(it.first.id)
                Utils.parcelWriteBoolean(dest, it.second)
            }
        }

        content.getFiles().let {
            dest.writeInt(it.size)
            it.forEach {
                dest.writeLong(it.id)
            }
        }
    }

    companion object CREATOR : Parcelable.Creator<IncludeRuleParcel> {

        override fun createFromParcel(parcel: Parcel): IncludeRuleParcel {
            return IncludeRule(
                parcel.readLong(),
                false,
                initialShare = RuleShareParcel.createFromParcel(parcel).content
            ).apply {
                for(i in 0 until parcel.readInt()) {
                    this.addDir(
                        RoomDB.DB_INSTANCE.mediaDirDao().getForId(parcel.readLong()),
                        Utils.parcelReadBoolean(parcel)
                    )
                }

                for(i in 0 until parcel.readInt()) {
                    this.addFile(RoomDB.DB_INSTANCE.mediaFileDao().getForId(parcel.readLong()))
                }
            }.let { IncludeRuleParcel(it) }
        }

        override fun newArray(size: Int): Array<IncludeRuleParcel?> {
            return arrayOfNulls(size)
        }
    }
}
