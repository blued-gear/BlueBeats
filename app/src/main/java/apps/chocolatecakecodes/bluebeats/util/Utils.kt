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

    fun dirTreeToMediaDir(path: String): MediaDir{
        if(path.isEmpty())
            throw IllegalArgumentException("path must not be empty")

        var dir: MediaDir? = null
        for(pathPart in path.split('/')){
            if(pathPart.isEmpty()) continue
            dir = MediaDir(pathPart, dir)
        }
        return dir!!
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
}

inline fun <T : AbstractVLCEvent?> IVLCObject<T>.using(retain: Boolean = true, block: () -> Unit){
    if(retain) this.retain()
    try{
        block()
    }finally {
        this.release()
    }
}