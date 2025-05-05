package apps.chocolatecakecodes.bluebeats.database.dao.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaDirEntity
import apps.chocolatecakecodes.bluebeats.media.model.MediaDirImpl
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileImpl
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import apps.chocolatecakecodes.bluebeats.util.castToOrNull
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit

@Dao
internal abstract class MediaDirDAO{

    private val cache: Cache<Long, MediaDir>

    private val mediaFileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
        TimerThread.INSTANCE.addInterval(TimeUnit.MINUTES.toMillis(5)) {
            cache.cleanUp()
            0
        }
    }

    //region public methods
    @Transaction
    open fun newDir(name: String, parent: Long): MediaDirImpl {
        val dirEntity = MediaDirEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent)
        val id = insertEntity(dirEntity)

        return getForId(id) as MediaDirImpl
    }

    fun getForId(id: Long): MediaDir {
        return cache.get(id) {
            val entity = getEntityForId(id)

            if(entity === null) {
                // this can happen when the dir is removed concurrently while a child-file is loaded
                return@get MediaNode.UNSPECIFIED_DIR
            }

            val parentSupplier = {
                if(entity.parent < 0)
                    null// all invalid MediaNode-IDs are < 0
                else
                    this.getForId(entity.parent)
            }

            MediaDirImpl.new(
                id,
                entity.name,
                parentSupplier
            )
        }
    }

    fun getDirsInDir(parent: MediaDirImpl): List<MediaDir> {
        val subdirIds = getSubdirIdsInDir(parent.id)
        return subdirIds.map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaDir {
        return getForId(getIdForNameAndParent(name, parent))
    }

    fun getAllDirs(): List<MediaDir> {
        return getAllIds().map {
            getForId(it)
        }
    }

    @Transaction
    open fun save(dir: MediaDirImpl) {
        updateEntity(
            MediaDirEntity(
                dir.id,
                dir.name,
                dir.parent?.id ?: MediaNode.NULL_PARENT_ID
            )
        )
    }

    @Transaction
    open fun delete(dir: MediaDirImpl) {
        dir.getFiles().forEach {
            it.castToOrNull<MediaFileImpl>()?.let { mediaFileDao.delete(it) }
        }
        dir.getDirs().forEach {
            it.castToOrNull<MediaDirImpl>()?.let { delete(it) }
        }

        deleteEntity(dir.id)
        cache.invalidate(dir.id)
    }

    @Query("SELECT EXISTS(SELECT id FROM MediaDirEntity WHERE id = :id);")
    abstract fun doesDirExist(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT id FROM MediaDirEntity WHERE name = :name AND parent = :parent);")
    abstract fun doesSubdirExist(name: String, parent: Long): Boolean
    //endregion

    //region db actions
    @Query("SELECT * FROM MediaDirEntity WHERE id = :id;")
    protected abstract fun getEntityForId(id: Long): MediaDirEntity?

    @Query("SELECT id FROM MediaDirEntity WHERE parent = :parent;")
    protected abstract fun getSubdirIdsInDir(parent: Long): List<Long>

    @Query("SELECT id FROM MediaDirEntity WHERE name = :name AND parent = :parent;")
    protected abstract fun getIdForNameAndParent(name: String, parent: Long): Long

    @Query("SELECT id FROM MediaDirEntity;")
    protected abstract fun getAllIds(): List<Long>

    @Insert
    protected abstract fun insertEntity(entity: MediaDirEntity): Long

    @Update
    protected abstract fun updateEntity(entity: MediaDirEntity)

    @Query("DELETE FROM MediaDirEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)
    //endregion
}
