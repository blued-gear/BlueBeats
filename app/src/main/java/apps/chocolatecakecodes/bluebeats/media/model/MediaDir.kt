package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import java.util.TreeSet

internal class MediaDir private constructor(
    internal val entityId: Long,
    override val name: String,
    parentProvider: () -> MediaDir?
): MediaNode() {

    companion object {
        fun new(
            entityId: Long,
            name: String,
            parentProvider: () -> MediaDir?
        ) = MediaDir(entityId, name, parentProvider).also {
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

    internal fun addDir(dir: MediaDir){
        if(this.entityId != dir.parent?.entityId)
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
        if(this.entityId != file.parent.entityId)
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

    fun createCopy(): MediaDir{
        return MediaDir(
            MediaNode.UNALLOCATED_NODE_ID,
            this.name,
            { this.parent }
        ).apply {
            // copy children because copy will be unable to load them (because of changed id)
            dirs.addAll(this@MediaDir.dirs)
            files.addAll(this@MediaDir.files)
        }
    }

    /** only compares if <code>other</code> is a MediaDir an its entity is equals */
    override fun equals(other: Any?): Boolean {
        if(other !is MediaDir)
            return false

        return this.entityId == other.entityId
    }

    /**
     * like <code>equals(other)</code>, with the difference all properties and children are compared (might result in recursive execution)
     */
    fun contentEquals(other: MediaDir?): Boolean{
        if(other === null)
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
}
