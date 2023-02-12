package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.util.Destructor
import org.videolan.libvlc.interfaces.IMedia
import java.util.*

abstract class MediaNode{ //TODO make serializable (all subclasses should have custom serializers)

    enum class Type{
        DIR, AUDIO, VIDEO, OTHER;
    }

    companion object{
        /**
         * used for single parsed files
         */
        val UNSPECIFIED_DIR: MediaDir = MediaDir("UNSPECIFIED", null)
    }

    abstract val type: Type
    abstract val name: String
    abstract val path: String

    abstract val parent: MediaNode?// should only be for runtime-caching

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int

    override fun toString(): String {
        return "$type: $path"
    }
}

/**
 * class for node in hierarchical representation of media-file-tree
 */
class MediaDir(override val name: String, parent: MediaDir?): MediaNode() {

    private val dirs: MutableSet<MediaDir>
    private val files: MutableSet<MediaFile>

    override val type: Type = Type.DIR
    @Transient// only cached for runtime
    override val path: String

    @Transient
    override val parent: MediaDir?// only root and UNSPECIFIED_DIR has parent with value null

    init{
        dirs = TreeSet(compareBy { it.name })
        files = TreeSet(compareBy { it.name })

        this.parent = parent
        path = (parent?.path ?: "") + "/" + name
    }

    internal fun addDir(dir: MediaDir){
        if(!this.shallowEquals(dir.parent))
            throw IllegalArgumentException("dir is not sub-item of this dir")
        dirs.add(dir)
    }
    internal fun removeDir(dir: MediaDir){
        dirs.remove(dir)
    }
    fun getDirs(): List<MediaDir>{
        return dirs.toList()
    }

    internal fun addFile(file: MediaFile){
        if(!this.shallowEquals(file.parent))
            throw IllegalArgumentException("file is not sub-item of this dir")
        files.add(file)
    }
    internal fun removeFile(file: MediaFile){
        files.remove(file)
    }
    fun getFiles(): List<MediaFile>{
        return files.toList()
    }

    fun findChild(name: String): MediaNode?{
        return dirs.firstOrNull { it.name == name } ?: files.firstOrNull { it.name == name }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is MediaDir)
            return false
        if(!shallowEquals(other))
            return false

        val thisChildren = this.getDirs()
        val otherChildren = other.getDirs()
        if(thisChildren.size != otherChildren.size)
            return false
        for(i in thisChildren.indices)
            if(thisChildren[i] != otherChildren[i])
                return false

        val thisFiles = this.getFiles()
        val otherFiles = other.getFiles()
        if(thisFiles.size != otherFiles.size)
            return false
        for(i in thisFiles.indices){
            if(thisFiles[i] != otherFiles[i])
                return false
        }

        return true
    }

    /**
     * like <code>equals(other)</code>, with the difference that just the path is compared
     */
    fun shallowEquals(other: MediaDir?): Boolean{
        if(other === null)
            return false
        return this.path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode() xor MediaDir::class.hashCode()
    }
}

/**
 * class for leave-node in hierarchical representation of media-file-tree
 */
class MediaFile(vlcMedia: IMedia, parent: MediaDir): MediaNode() {//TODO more attrs (e.g. tags)

    override val type: Type
    override val name: String
    override val path: String

    @Transient
    override val parent: MediaDir

    @Transient
    private val vlcMedia: IMedia

    init{
        vlcMedia.retain()

        if(!vlcMedia.isParsed)
            vlcMedia.parse()

        if(vlcMedia.type != IMedia.Type.File)
            throw IllegalArgumentException("media must be a file")

        this.vlcMedia = vlcMedia

        this.parent = parent
        path = vlcMedia.uri.path ?: throw IllegalArgumentException("media has invalid path")
        name = vlcMedia.uri.lastPathSegment ?: throw IllegalArgumentException("media has invalid path")

        // check for audio or video file

        var isVideo = false
        for(i in 0 until vlcMedia.trackCount) {
            if (vlcMedia.getTrack(i).type == IMedia.Track.Type.Video) {
                isVideo = true
                break
            }
        }
        if(isVideo){
            this.type = Type.VIDEO
        }else{
            var isAudio = false
            for (i in 0 until vlcMedia.trackCount) {
                if (vlcMedia.getTrack(i).type == IMedia.Track.Type.Audio) {
                    isAudio = true
                    break
                }
            }

            if(isAudio)
                this.type = Type.AUDIO
            else
                this.type = Type.OTHER
        }
        //type = Type.OTHER

        Destructor.registerDestructor(this) {
            this.vlcMedia.release()
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is MediaFile)
            return false
        if(!shallowEquals(other))
            return false
        //TODO compare all attributes
        return true
    }

    /**
     * like <code>equals(other)</code>, with the difference that just the type and path are compared
     */
    fun shallowEquals(other: MediaFile?): Boolean{
        if(other === null)
            return false
        return  this.type == other.type && this.path == other.path
    }

    override fun hashCode(): Int {
        return Objects.hash(path, type) xor MediaFile::class.hashCode()
    }
}