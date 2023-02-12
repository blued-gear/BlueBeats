package apps.chocolatecakecodes.bluebeats.database

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
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
        return cache.get(id) {
            MediaDir(getEntityForId(id))
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
        val fileEntity = MediaFileEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent, type,
            TagFields(), null)
        val id = insertEntity(fileEntity)

        return getForId(id)
    }

    fun newFile(from: MediaFile): MediaFile{
        val newFile = newFile(from.name, from.type, from.parent.entity.id)

        // copy extra attributes (TODO update whenever attributes changes)
        newFile.mediaTags = from.mediaTags
        newFile.userTags = from.userTags
        newFile.chapters = from.chapters
        save(newFile)

        return newFile
    }

    fun getForId(id: Long): MediaFile{
        return cache.get(id) {
            MediaFile(getEntityForId(id))
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
        RoomDB.DB_INSTANCE.userTagDao().saveUserTagsOfFile(file, file.userTags)
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

@Dao
internal abstract class UserTagsDAO{

    //region public methods
    fun getUserTagsForFile(file: MediaFile): List<String>{
        return getUserTags(file.entity.id).map {
            it.name
        }
    }

    fun getAllUserTags(): List<String>{
        return getAllUserTagEntities().map{
            it.name
        }
    }

    /**
     * tags are OR combined
     */
    fun getFilesForTags(tags: List<String>): List<MediaFile>{
        val fileDAO = RoomDB.DB_INSTANCE.mediaFileDao()
        return getFileIdsWithTags(tags).map{
            fileDAO.getForId(it)
        }
    }

    fun saveUserTagsOfFile(file: MediaFile, tags: List<String>){
        val tagsSet = tags.toHashSet()
        val existingTags = getUserTagEntityForNames(tags).toHashSet()
        val existingTagNames = existingTags.map{
            it.name
        }.toHashSet()

        // save all missing tags
        tagsSet.minus(existingTagNames).forEach{
            saveUserTag(UserTagEntity(MediaNode.UNALLOCATED_NODE_ID, it))
        }

        // delete relations for removed tags
        existingTags.filter {
            !tagsSet.contains(it.name)
        }.forEach {
            removeRelation(it.id, file.entity.id)
        }

        // save relations
        tags.map {
            getUserTagEntityForNames(listOf(it)).first()
        }.mapIndexed { pos, entity ->
            UserTagRelation(MediaNode.UNALLOCATED_NODE_ID, entity.id, file.entity.id, pos)
        }.forEach {
            saveUserTagRelation(it)
        }
    }

    /**
     * removes all UserTags from DB which have no file associated
     */
    @Query("DELETE FROM UserTagEntity WHERE id NOT IN (SELECT tag FROM UserTagRelation);")
    abstract fun removeOrphanUserTags()
    //endregion

    //region db actions
    @Query("SELECT tag.id, tag.name FROM MediaFileEntity AS file " +
            "INNER JOIN UserTagRelation AS rel ON file.id = rel.file " +
            "INNER JOIN UserTagEntity AS tag ON tag.id = rel.tag " +
            "WHERE file.id = :fileId ORDER BY pos;")
    protected abstract fun getUserTags(fileId: Long): List<UserTagEntity>

    @Query("SELECT * FROM UserTagEntity")
    protected abstract fun getAllUserTagEntities(): List<UserTagEntity>

    @Query("SELECT file.id FROM MediaFileEntity AS file " +
            "INNER JOIN UserTagRelation AS rel ON file.id = rel.file " +
            "INNER JOIN UserTagEntity AS tag ON tag.id = rel.tag " +
            "WHERE tag.name IN (:tags);")
    protected abstract fun getFileIdsWithTags(tags: List<String>): List<Long>

    @Query("SELECT * FROM UserTagEntity WHERE name IN (:names);")
    protected abstract fun getUserTagEntityForNames(names: List<String>): List<UserTagEntity>

    @Insert(onConflict = IGNORE)
    protected abstract fun saveUserTagRelation(tagRelation: UserTagRelation)

    @Insert(onConflict = IGNORE)
    protected abstract fun saveUserTag(tag: UserTagEntity)

    @Query("DELETE FROM UserTagRelation WHERE tag = :tag AND file = :file;")
    protected abstract fun removeRelation(tag: Long, file: Long)

    @Delete
    protected abstract fun removeUserTag(tag: UserTagEntity)
    //endregion
}
