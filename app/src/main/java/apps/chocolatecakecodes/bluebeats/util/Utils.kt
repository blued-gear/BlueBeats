package apps.chocolatecakecodes.bluebeats.util

import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.google.gson.reflect.TypeToken
import org.videolan.libvlc.interfaces.AbstractVLCEvent
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCObject
import java.io.File
import java.lang.reflect.Type

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

    inline fun <reified T> captureType(): Type{
        return object : TypeToken<T>(){}.type
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
}

inline fun <T : IVLCObject<E>, E : AbstractVLCEvent?> T.using(retain: Boolean = true, block: (T) -> Unit){
    if(retain) this.retain()
    try{
        block(this)
    }finally {
        this.release()
    }
}