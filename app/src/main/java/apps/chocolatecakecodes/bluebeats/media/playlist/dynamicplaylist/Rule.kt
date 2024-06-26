package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.util.Utils

internal typealias GenericRule = Rule<*>

//TODO maybe rules should have names

/**
 * @param T type of the implementing class
 */
internal sealed interface Rule<T> : Parcelable {

    /**
     * determines how much items a rule should add to the resulting collection <br/>
     * there are four modes: relative (isRelative = true, value >= 0), absolute (isRelative = false, value >= 0),
     *  even (isRelative = true, value = -1; all with even will get the same relative value),
     *  unlimited (isRelative = false, value = -1)
     */
    data class Share(
        val value: Float,
        /** if true the value is a percentage between 0 and 1.0;
         *     else it is the absolute amount of items which should be generated (should be cast to int) */
        val isRelative: Boolean
    ) : Parcelable {

        fun modeRelative() = isRelative && value >= 0
        fun modeAbsolute() = !isRelative && value >= 0
        fun modeEven() = isRelative && value == -1f
        fun modeUnlimited() = !isRelative && value == -1f

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeFloat(value)
            Utils.parcelWriteBoolean(dest, isRelative)
        }

        companion object CREATOR : Parcelable.Creator<Share> {

            override fun createFromParcel(parcel: Parcel): Share {
                return Share(
                    parcel.readFloat(),
                    Utils.parcelReadBoolean(parcel)
                )
            }

            override fun newArray(size: Int): Array<Share?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     * only one original may exist at any given time; multiple copies may exist; only the original can be stored to DB
     * @see Rule.copy
     */
    val isOriginal: Boolean

    var share: Share

    /**
     * returns a collection of media from this rule
     * @param amount the expected amount of media to return; if -1 then the amount is unlimited
     * @param exclude set of files which must not be contained in the resulting set
     */
    fun generateItems(amount: Int, exclude: Set<PlaylistItem>): List<PlaylistItem>

    /**
     * Returns a deep-copy of the rule and all its subrules.
     * The returned rule will have isOriginal set to false
     */
    fun copy(): T

    /**
     * Applies all properties of the given rule to this instance.
     * If this rule is a group then this method will be called on all subrules.
     */
    fun applyFrom(other: T)

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}
