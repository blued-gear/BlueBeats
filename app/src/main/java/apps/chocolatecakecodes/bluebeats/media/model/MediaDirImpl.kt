package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import java.util.TreeSet

internal class MediaDirImpl private constructor(
    override val id: Long,
    override val name: String,
    parentProvider: () -> MediaDir?
): MediaDir() {

    companion object {
        fun new(
            entityId: Long,
            name: String,
            parentProvider: () -> MediaDir?
        ) = MediaDirImpl(entityId, name, parentProvider).also {
            // trigger caching of hashCode (else it might happen that a DB-query in the UI-Thread gets executed)
            it.hashCode()
        }
    }

    private val dirs: MutableSet<MediaDir> by CachedReference(this, NODE_CACHE_TIME) {
        val collection = TreeSet<MediaDir>(compareBy { it.name })
        collection.addAll(RoomDB.DB_INSTANCE.mediaDirDao().getDirsInDir(this))
        return@CachedReference collection
    }
    private val files: MutableSet<MediaFile> by CachedReference(this, NODE_CACHE_TIME) {
        val collection = TreeSet<MediaFile>(compareBy { it.name })
        collection.addAll(RoomDB.DB_INSTANCE.mediaFileDao().getFilesInDir(this))
        return@CachedReference collection
    }

    override val parent: MediaDir? by CachedReference(this, NODE_CACHE_TIME) {
        parentProvider()
    }
    override val path: String by lazy {
        return@lazy if(parent === null)
                name
            else
                parent!!.path + name + "/"
    }

    internal fun addDir(dir: MediaDir) {
        if(this.id != dir.parent?.id)
            throw IllegalArgumentException("dir is not sub-item of this dir")
        dirs.add(dir)
    }

    internal fun removeDir(dir: MediaDir) {
        dirs.remove(dir)
    }

    override fun getDirs(): List<MediaDir> {
        return dirs.toList()
    }

    internal fun addFile(file: MediaFile) {
        if(this.id != file.parent?.id)
            throw IllegalArgumentException("file is not sub-item of this dir")
        files.add(file)
    }

    internal fun removeFile(file: MediaFile) {
        files.remove(file)
    }

    override fun getFiles(): List<MediaFile> {
        return files.toList()
    }

    override fun findChild(name: String): MediaNode? {
        return dirs.firstOrNull { it.name == name } ?: files.firstOrNull { it.name == name }
    }

    override fun createCopy(): MediaDirImpl {
        return MediaDirImpl(
            UNALLOCATED_NODE_ID,
            this.name,
            { this.parent }
        ).apply {
            // copy children because copy will be unable to load them (because of changed id)
            dirs.addAll(this@MediaDirImpl.dirs)
            files.addAll(this@MediaDirImpl.files)
        }
    }

    /** only compares if <code>other</code> is a MediaDir an its entity is equals */
    override fun equals(that: Any?): Boolean {
        if(that !is MediaDirImpl)
            return false

        return this.id == that.id
    }

    override fun hashCode(): Int {
        return arrayOf(this::class.qualifiedName!!, path).contentHashCode()
    }

    override fun toString(): String {
        return "MediaDir: $path"
    }

    override fun deepEquals(that: MediaDir?): Boolean {
        if(that === null)
            return false

        val thisChildren = this.getDirs()
        val otherChildren = that.getDirs()
        if(thisChildren.size != otherChildren.size)
            return false
        for(i in thisChildren.indices)
            if(thisChildren[i] != otherChildren[i])
                return false

        val thisFiles = this.getFiles()
        val otherFiles = that.getFiles()
        if(thisFiles.size != otherFiles.size)
            return false
        for(i in thisFiles.indices){
            if(thisFiles[i] != otherFiles[i])
                return false
        }

        return true
    }
}
