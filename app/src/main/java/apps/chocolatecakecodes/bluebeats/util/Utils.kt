package apps.chocolatecakecodes.bluebeats.util

import androidx.documentfile.provider.DocumentFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import org.videolan.libvlc.interfaces.AbstractVLCEvent
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCObject
import java.io.File

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
     * creates an empty array (size == 0) of the given type
     */
    inline fun <reified T> emptyArray(): Array<T>{
        return Array<T>(0){throw AssertionError("nothing to initialize")}
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