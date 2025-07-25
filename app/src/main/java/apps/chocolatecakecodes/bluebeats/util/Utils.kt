package apps.chocolatecakecodes.bluebeats.util

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.Parcel
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import org.videolan.libvlc.interfaces.AbstractVLCEvent
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCObject
import java.io.File
import kotlin.math.roundToInt

object Utils {

    fun vlcMediaToDocumentFile(media: IMedia): DocumentFile{
        return DocumentFile.fromFile(File(media.uri.path!!))
    }

    /**
     * path must be normalized (must not contain //, .. and so on)
     */
    fun parentDir(path: String): String{
        var idx = path.lastIndexOf('/')
        if(idx == -1)
            return path + "/"
        if(idx == path.lastIndex && path.length > 1)
            idx = path.lastIndexOf('/', path.lastIndex - 1)
        return path.substring(0, idx) + "/"
    }

    /**
     * formats time to a string with format (hh:)mm:ss; calls [Utils.formatTime]
     *  with param <code>withHours = (lenCapped / (60 * 60 * 1000)) > 0</code>
     * @param ms time in milliseconds
     * @return the formatted time-string
     */
    fun formatTime(ms: Long): String{
        val withHours = (ms / (60 * 60 * 1000)) > 0
        return formatTime(ms, withHours)
    }

    /**
     * formats time to a string with format [hh:]mm:ss
     * @param ms time in milliseconds
     * @param withHours if true hours will be included, else the minutes will be displayed as >= 60
     * @return the formatted time-string
     */
    fun formatTime(ms: Long, withHours: Boolean): String{
        var varMs = ms
        var factor: Long = if(withHours) 60 * 60 * 1000 else 60 * 1000

        val hours: Long
        if(withHours){
            hours = varMs / factor
            varMs -= hours * factor
            factor /= 60
        }else{
            hours = -1
        }

        val minutes = varMs / factor
        varMs -= minutes * factor
        factor /= 60

        val seconds = varMs / factor

        if(withHours)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else
            return String.format("%02d:%02d", minutes, seconds)
    }

    fun isUiThread(): Boolean{
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * tries to set the value to the LiveData immediately if in UI-Thread,
     * else it will use postValue()
     */
    fun <T> trySetValueImmediately(data: MutableLiveData<T>, value: T){
        if(isUiThread())
            data.setValue(value)
        else
            data.postValue(value)
    }

    /**
     * Creates and shows a popup.
     * The popup will be as big as the anchor-view.
     * @param context the context to use
     * @param anchor the view over which the popup will be shown
     * @param contentLayout the id of the layout to show
     * @param closeOnClick if true every uncaught ACTION_UP on the contentLayout will close the popup
     * @param initContent called with the inflated contentLayout before the popup is shown
     */
    fun showPopup(context: Context, anchor: View,
                  contentLayout: Int,
                  closeOnClick: Boolean,
                  initContent: ((View) -> Unit)): PopupWindow {
        val inflater = ContextCompat.getSystemService(context, LayoutInflater::class.java)!!
        val content = inflater.inflate(contentLayout, null)

        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            true
        )

        initContent(content)

        if(closeOnClick) {
            content.setOnTouchListener { v, event ->
                if(event.actionMasked == MotionEvent.ACTION_UP) {
                    v.performClick()

                    popup.dismiss()
                }

                true
            }
        }

        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0)

        return popup
    }

    /**
     * @return (added, deleted, same)
     */
    fun <T> diffChanges(old: Set<T>, new: Set<T>): Triple<Set<T>, Set<T>, Set<T>> {
        return Triple(
            new.minus(old),
            old.minus(new),
            old.intersect(new)
        )
    }

    fun parcelWriteBoolean(dest: Parcel, bool: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            dest.writeBoolean(bool)
        else
            dest.writeInt(if(bool) 1 else 0)
    }
    fun parcelReadBoolean(src: Parcel): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            src.readBoolean()
        else
            src.readInt() != 0
    }

    /**
     * compares the strings as lowercase and if they are equal in their original case
     */
    fun compareStringNaturally(o1: String, o2: String): Int {
        if(o1 === o2)
            return 0

        val lcCmp = o1.lowercase().compareTo(o2.lowercase())
        if(lcCmp != 0)
            return lcCmp

        return o1.compareTo(o2)
    }

    fun dpToPx(ctx: Context, dp: Int): Int {
        return (ctx.resources.displayMetrics.density * dp).roundToInt()
    }
}

inline fun <T : IVLCObject<E>, E : AbstractVLCEvent?, R: Any?> T.using(retain: Boolean = true, block: (T) -> R): R {
    if(retain) this.retain()
    try {
        return block(this)
    } finally {
        this.release()
    }
}

/**
 * returns n elements of this Iterable or all if amount == -1
 */
fun <T, I : Iterable<T>> I.takeOrAll(amount: Int): List<T> {
    return if(amount == -1)
        this.toList()
    else
        this.take(amount)
}

/**
 * returns n elements of this Iterable or all if amount == -1
 */
fun <T, I : Sequence<T>> I.takeOrAll(amount: Int): Sequence<T> {
    return if(amount == -1)
        this
    else
        this.take(amount)
}

/**
 * removes the first element which satisfies the filter
 * @return the removed element or null if none matched the filter
 */
fun <T, I : MutableIterable<T>> I.removeIfSingle(filter: (T) -> Boolean): T? {
    val iter = this.iterator()
    while (iter.hasNext()) {
        val el = iter.next()
        if (filter(el)) {
            iter.remove()
            return el
        }
    }

    return null
}

inline fun <reified T> Any.castTo(): T {
    return this as T
}
inline fun <reified T> Any.castToOrNull(): T? {
    return this as? T
}
