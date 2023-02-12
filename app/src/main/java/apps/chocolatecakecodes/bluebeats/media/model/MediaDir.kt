package apps.chocolatecakecodes.bluebeats.media.model

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import java.util.*

class MediaDir internal constructor(internal val entity: MediaDirEntity): MediaNode() {

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
        if(entity.parent < 0) return@CachedReference null// all invalid MediaNode-IDs are < 0
        val dao = RoomDB.DB_INSTANCE.mediaDirDao()
        return@CachedReference dao.getForId(entity.parent)
    }
    override val path: String by lazy {
        return@lazy if(parent === null)
                name
            else
                parent!!.path + name + "/"
    }
    override val name: String by entity::name

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

    fun createCopy(): MediaDir{
        val copy = MediaDir(entity.copy(id = MediaNode.UNALLOCATED_NODE_ID))
        // copy children because copy will be unable to load them (because of changed id)
        copy.dirs.addAll(dirs)
        copy.files.addAll(files)
        return copy
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
}

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class, parentColumns = ["id"], childColumns = ["parent"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaDirEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long
)