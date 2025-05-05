package apps.chocolatecakecodes.bluebeats.util.serializers

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB

class TimeSpanRuleParcel(
    override val content: TimeSpanRule
) : RuleParcel {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(content.isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(content.id)
        RuleShareParcel(content.share).writeToParcel(dest, flags)

        dest.writeLong(content.file.id)
        dest.writeLong(content.startMs)
        dest.writeLong(content.endMs)
        dest.writeString(content.description)
    }

    companion object CREATOR : Parcelable.Creator<TimeSpanRuleParcel> {

        override fun createFromParcel(source: Parcel): TimeSpanRuleParcel {
            return TimeSpanRule(
                id = source.readLong(),
                isOriginal = false,
                initialShare = RuleShareParcel.createFromParcel(source).content,
                file = source.readLong().let { fileId ->
                    if(fileId == MediaNode.INVALID_FILE.id)
                        MediaNode.INVALID_FILE
                    else
                        RoomDB.DB_INSTANCE.mediaFileDao().getForId(fileId)
                } ,
                startMs = source.readLong(),
                endMs = source.readLong(),
                description = source.readString()!!
            ).let { TimeSpanRuleParcel(it) }
        }

        override fun newArray(size: Int): Array<TimeSpanRuleParcel?> {
            return arrayOfNulls(size)
        }
    }
}
