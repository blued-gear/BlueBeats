package apps.chocolatecakecodes.bluebeats.database

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.media.model.*
import apps.chocolatecakecodes.bluebeats.media.model.MediaDirEntity
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileEntity
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder

@Dao
internal abstract class MediaDirDAO{

    private val cache: Cache<Long, MediaDir>

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
    }

    //region public methods
    fun newDir(name: String, parent: Long): MediaDir{
        val dirEntity = MediaDirEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent)
        val id = insertEntity(dirEntity)

        return getForId(id)
    }

    fun getForId(id: Long): MediaDir{
        synchronized(this) {
            val cached = cache.getIfPresent(id)
            if (cached !== null) return cached

            val entity = getEntityForId(id)
            val dir = MediaDir(entity)
            cache.put(id, dir)
            return dir
        }
    }

    fun getDirsInDir(parent: MediaDir): List<MediaDir>{
        val subdirIds = getSubdirIdsInDir(parent.entity.id)
        return subdirIds.map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaDir{
        return getForId(getIdForNameAndParent(name, parent))
    }

    fun save(dir: MediaDir){
        updateEntity(dir.entity)
    }

    fun delete(dir: MediaDir){
        // children will be deleted too, because the foreign-keys are set to CASCADE
        deleteEntity(dir.entity)
        cache.invalidate(dir.entity.id)
    }

    @Query("SELECT EXISTS(SELECT id FROM MediaDirEntity WHERE id = :id);")
    abstract fun doesDirExist(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT id FROM MediaDirEntity WHERE name = :name AND parent = :parent);")
    abstract fun doesSubdirExist(name: String, parent: Long): Boolean
    //endregion

    //region db actions
    @Query("SELECT * FROM MediaDirEntity WHERE id = :id;")
    protected abstract fun getEntityForId(id: Long): MediaDirEntity

    @Query("SELECT id FROM MediaDirEntity WHERE parent = :parent;")
    protected abstract fun getSubdirIdsInDir(parent: Long): List<Long>

    @Query("SELECT id FROM MediaDirEntity WHERE name = :name AND parent = :parent;")
    protected abstract fun getIdForNameAndParent(name: String, parent: Long): Long

    @Insert
    protected abstract fun insertEntity(entity: MediaDirEntity): Long

    @Update
    protected abstract fun updateEntity(entity: MediaDirEntity)

    @Delete
    protected abstract fun deleteEntity(entity: MediaDirEntity)
    //endregion
}

@Dao
internal abstract class MediaFileDAO{

    private val cache: Cache<Long, MediaFile>

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
    }

    //region public methods
    fun newFile(name: String, type: MediaFile.Type, parent: Long): MediaFile{
        val fileEntity = MediaFileEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent, type)
        val id = insertEntity(fileEntity)

        return getForId(id)
    }

    fun getForId(id: Long): MediaFile{
        synchronized(this) {
            val cached = cache.getIfPresent(id)
            if (cached !== null) return cached

            val entity = getEntityForId(id)
            val dir = MediaFile(entity)
            cache.put(id, dir)
            return dir
        }
    }

    fun getFilesInDir(parent: MediaDir): List<MediaFile>{
        return getFileIdsInDir(parent.entity.id).map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaFile{
        return getForId(getFileIdForNameAndParent(name, parent))
    }

    fun save(file: MediaFile){
        updateEntity(file.entity)
    }

    fun delete(file: MediaFile){
        deleteEntity(file.entity)
        cache.invalidate(file.entity.id)
    }

    @Query("SELECT EXISTS(SELECT id FROM MediaFileEntity WHERE id = :id);")
    abstract fun doesFileExist(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT id FROM MediaFileEntity WHERE name = :name AND parent = :parent);")
    abstract fun doesFileInDirExist(name: String, parent: Long): Boolean
    //endregion

    //region db actions
    @Query("SELECT * FROM MediaFileEntity WHERE id = :id;")
    protected abstract fun getEntityForId(id: Long): MediaFileEntity

    @Query("SELECT id FROM MediaFileEntity WHERE parent = :parent;")
    protected abstract fun getFileIdsInDir(parent: Long): List<Long>

    @Query("SELECT id FROM MediaFileEntity WHERE name = :name AND parent = :parent;")
    protected abstract fun getFileIdForNameAndParent(name: String, parent: Long): Long

    @Insert
    protected abstract fun insertEntity(entity: MediaFileEntity): Long

    @Update
    protected abstract fun updateEntity(entity: MediaFileEntity)

    @Delete
    protected abstract fun deleteEntity(entity: MediaFileEntity)
    //endregion
}