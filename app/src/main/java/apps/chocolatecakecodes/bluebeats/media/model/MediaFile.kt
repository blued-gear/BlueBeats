package apps.chocolatecakecodes.bluebeats.media.model

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import java.util.*

class MediaFile internal constructor(internal val entity: MediaFileEntity): MediaNode(){

    override val parent: MediaDir by CachedReference(this, NODE_CACHE_TIME){
        val dao = RoomDB.DB_INSTANCE.mediaDirDao()
        return@CachedReference dao.getForId(entity.parent)
    }
    override val path: String by lazy{
        parent.path + name
    }
    override val name: String by entity::name
    var type: Type by entity::type

    fun createCopy(): MediaFile{
        return MediaFile(entity.copy(id = MediaNode.UNALLOCATED_NODE_ID))
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

    enum class Type{
        AUDIO, VIDEO, OTHER
    }
}

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class, parentColumns = ["id"], childColumns = ["parent"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long,
    @ColumnInfo(name = "type", index = true) var type: MediaFile.Type
)