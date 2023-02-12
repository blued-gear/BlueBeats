package apps.chocolatecakecodes.bluebeats.database

import androidx.room.*
import androidx.room.OnConflictStrategy.IGNORE
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
import apps.chocolatecakecodes.bluebeats.taglib.TagParser
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit

@Dao
internal abstract class MediaDirDAO{

    private val cache: Cache<Long, MediaDir>

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
        TimerThread.INSTANCE.addInterval(TimeUnit.MINUTES.toMillis(5)) {
            cache.cleanUp()
            0
        }
    }

    //region public methods
    @Transaction
    open fun newDir(name: String, parent: Long): MediaDir{
        val dirEntity = MediaDirEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent)
        val id = insertEntity(dirEntity)

        return getForId(id)
    }

    fun getForId(id: Long): MediaDir{
        return cache.get(id) {
            val entity = getEntityForId(id)

            val parentSupplier = {
                if(entity.parent < 0)
                    null// all invalid MediaNode-IDs are < 0
                else
                    this.getForId(entity.parent)
            }

            MediaDir(
                id,
                entity.name,
                parentSupplier
            )
        }
    }

    fun getDirsInDir(parent: MediaDir): List<MediaDir>{
        val subdirIds = getSubdirIdsInDir(parent.entityId)
        return subdirIds.map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaDir{
        return getForId(getIdForNameAndParent(name, parent))
    }

    @Transaction
    open fun save(dir: MediaDir){
        updateEntity(
            MediaDirEntity(
                dir.entityId,
                dir.name,
                dir.parent?.entityId ?: MediaNode.NULL_PARENT_ID
            )
        )
    }

    @Transaction
    open fun delete(dir: MediaDir){
        // children will be deleted too, because the foreign-keys are set to CASCADE
        deleteEntity(dir.entityId)
        cache.invalidate(dir.entityId)
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

    @Query("DELETE FROM MediaDirEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)
    //endregion
}

@Dao
internal abstract class MediaFileDAO{

    private val cache: Cache<Long, MediaFile>

    private val mediaDirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }
    private val id3TagDao: ID3TagDAO by lazy {
        RoomDB.DB_INSTANCE.id3TagDao()
    }
    private val userTagDao: UserTagsDAO by lazy {
        RoomDB.DB_INSTANCE.userTagDao()
    }

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
        TimerThread.INSTANCE.addInterval(TimeUnit.MINUTES.toMillis(5)) {
            cache.cleanUp()
            0
        }
    }

    //region public methods
    fun createCopy(from: MediaFile): MediaFile{
        val mediaTagsCpy = from.mediaTags.clone()
        val chaptersCpy = from.chapters
        val usertagsCpy = from.userTags

        return MediaFile(
            MediaNode.UNALLOCATED_NODE_ID,
            from.name,
            from.type,
            { from.parent },
            { mediaTagsCpy },
            { chaptersCpy },
            { usertagsCpy }
        )
    }

    @Transaction
    open fun newFile(name: String, type: MediaFile.Type, parent: Long): MediaFile{
        val fileEntity = MediaFileEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent, type, null)
        val id = insertEntity(fileEntity)

        return getForId(id)
    }

    @Transaction
    open fun newFile(from: MediaFile): MediaFile{
        val newFile = newFile(from.name, from.type, from.parent.entityId)

        // copy extra attributes (TODO update whenever attributes changes)
        newFile.mediaTags = from.mediaTags.clone()
        newFile.userTags = from.userTags
        newFile.chapters = from.chapters
        save(newFile)

        return newFile
    }

    fun getForId(id: Long): MediaFile{
        return cache.get(id) {
            val entity = getEntityForId(id)

            val parentSupplier = {
                mediaDirDao.getForId(entity.parent)
            }
            val mediaTagsSupplier = {
                RoomDB.DB_INSTANCE.id3TagDao().getTagsOfFile(id)
            }
            val chaptersSupplier: () -> List<Chapter>? = {
                if(entity.chaptersJson.isNullOrEmpty())
                    null
                else
                    TagParser.Serializer.GSON.fromJson(entity.chaptersJson,
                        Utils.captureType<List<Chapter>>())
            }
            val usertagsSupplier = {
                if(entity.id == MediaNode.UNALLOCATED_NODE_ID)// can not load tags from db if this file is not saved
                    emptyList()
                else
                    RoomDB.DB_INSTANCE.userTagDao().getUserTagsForFile(id)
            }

            MediaFile(
                id,
                entity.name,
                entity.type,
                parentSupplier,
                mediaTagsSupplier, chaptersSupplier, usertagsSupplier
            )
        }
    }

    fun getFilesInDir(parent: MediaDir): List<MediaFile>{
        return getFileIdsInDir(parent.entityId).map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaFile{
        return getForId(getFileIdForNameAndParent(name, parent))
    }

    @Transaction
    open fun save(file: MediaFile){
        val chaptersJson = file.chapters?.let {
            TagParser.Serializer.GSON.toJson(it)
        }
        updateEntity(
            MediaFileEntity(
                file.entityId,
                file.name,
                file.parent.entityId,
                file.type,
                chaptersJson
            )
        )

        userTagDao.saveUserTagsOfFile(file, file.userTags)
        id3TagDao.saveTagOfFile(file)
    }

    @Transaction
    open fun delete(file: MediaFile){
        userTagDao.deleteUserTagsOfFile(file)
        id3TagDao.deleteAllEntriesOfFile(file)
        deleteEntity(file.entityId)
        cache.invalidate(file.entityId)
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

    @Query("DELETE FROM MediaFileEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)

    //endregion
}

@Dao
internal abstract class ID3TagDAO {

    private val mediaFileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }

    // region public methods
    fun getTagsOfFile(fileId: Long): TagFields {
        return findTagsForFile(fileId).associate {
            it.type to it.value
        }.let {
            mapToTagFields(it)
        }
    }

    fun getFilesWithTag(type: String, value: String): List<MediaFile> {
        return findFilesWithTag(type, value).map {
            mediaFileDao.getForId(it)
        }
    }

    @Query("SELECT DISTINCT type FROM ID3TagEntity;")
    abstract fun getAllTagTypes(): List<String>

    @Query("SELECT DISTINCT value FROM ID3TagEntity WHERE type = :type;")
    abstract fun getAllTypeValues(type: String): List<String>

    @Transaction
    open fun saveTagOfFile(file: MediaFile) {
        val existingTagTypes = findTagsForFile(file.entityId).map { it.type }.toSet()
        val currentTags = tagFieldsToMap(file.mediaTags)
        val currentTagTypes = currentTags.map { it.key }.toSet()

        Utils.diffChanges(existingTagTypes, currentTagTypes).let { (added, deleted, same) ->
            deleted.forEach {
                deleteEntityForFile(file.entityId, it)
            }

            added.forEach {
                insertEntity(ID3TagEntity(0, file.entityId, it, currentTags[it]!!))
            }

            same.forEach {
                updateEntity(file.entityId, it, currentTags[it]!!)
            }
        }
    }

    fun deleteAllEntriesOfFile(file: MediaFile) {
        deleteAllEntitiesForFile(file.entityId)
    }
    //endregion

    //region db actions
    @Query("SELECT * FROM ID3TagEntity WHERE file = :file;")
    protected abstract fun findTagsForFile(file: Long): List<ID3TagEntity>

    @Query("SELECT file FROM ID3TagEntity WHERE type = :type AND value = :value;")
    protected abstract fun findFilesWithTag(type: String, value: String): List<Long>

    @Insert
    protected abstract fun insertEntity(entity: ID3TagEntity)

    @Query("UPDATE ID3TagEntity SET value = :value WHERE file = :file AND type = :type;")
    protected abstract fun updateEntity(file: Long, type: String, value: String)

    @Query("DELETE FROM ID3TagEntity WHERE file = :file;")
    protected abstract fun deleteAllEntitiesForFile(file: Long)

    @Query("DELETE FROM ID3TagEntity WHERE file = :file AND type = :type;")
    protected abstract fun deleteEntityForFile(file: Long, type: String)
    //endregion

    //region private methods
    private fun tagFieldsToMap(tags: TagFields): Map<String, String> {
        val ret = HashMap<String, String>()

        if(!tags.title.isNullOrEmpty())
            ret.put("title", tags.title)
        if(!tags.artist.isNullOrEmpty())
            ret.put("artist", tags.artist)
        if(tags.length > 0)
            ret.put("length", tags.length.toString())

        return ret
    }

    fun mapToTagFields(tags: Map<String, String>): TagFields {
        val ret = TagFields()

        tags["title"]?.let { ret.title = it }
        tags["artist"]?.let { ret.artist = it }
        tags["length"]?.let { ret.length = it.toLong() }

        return ret
    }
    //endregion
}

@Dao
internal abstract class UserTagsDAO{

    //region public methods
    fun getUserTagsForFile(fileId: Long): List<String>{
        return getUserTags(fileId).map {
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
     * @return Map<file, List<tags>>
     */
    fun getFilesForTags(tags: List<String>): Map<MediaFile, List<String>>{
        val fileDAO = RoomDB.DB_INSTANCE.mediaFileDao()
        return getFileIdsWithTags(tags)
            .groupBy({ it.fileId }, { it.tag })
            .mapKeys {
                fileDAO.getForId(it.key)
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
            removeRelation(it.id, file.entityId)
        }

        // save relations
        tags.map {
            getUserTagEntityForNames(listOf(it)).first()
        }.mapIndexed { pos, entity ->
            UserTagRelation(MediaNode.UNALLOCATED_NODE_ID, entity.id, file.entityId, pos)
        }.forEach {
            saveUserTagRelation(it)
        }
    }

    fun deleteUserTagsOfFile(file: MediaFile) {
        removeRelationsForFile(file.entityId)
        removeOrphanUserTags()
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

    @Query("SELECT file.id AS fileId, tag.name AS tagVal " +
            "FROM MediaFileEntity AS file " +
            "INNER JOIN UserTagRelation AS rel ON file.id = rel.file " +
            "INNER JOIN UserTagEntity AS tag ON tag.id = rel.tag " +
            "WHERE tag.name IN (:tags);")
    protected abstract fun getFileIdsWithTags(tags: List<String>): List<FileWithTag>

    @Query("SELECT * FROM UserTagEntity WHERE name IN (:names);")
    protected abstract fun getUserTagEntityForNames(names: List<String>): List<UserTagEntity>

    @Insert(onConflict = IGNORE)
    protected abstract fun saveUserTagRelation(tagRelation: UserTagRelation)

    @Insert(onConflict = IGNORE)
    protected abstract fun saveUserTag(tag: UserTagEntity)

    @Query("DELETE FROM UserTagRelation WHERE tag = :tag AND file = :file;")
    protected abstract fun removeRelation(tag: Long, file: Long)

    @Query("DELETE FROM UserTagRelation WHERE file = :file;")
    protected abstract fun removeRelationsForFile(file: Long)

    @Delete
    protected abstract fun removeUserTag(tag: UserTagEntity)

    protected data class FileWithTag(
        @ColumnInfo(name = "fileId") val fileId: Long,
        @ColumnInfo(name = "tagVal") val tag: String
    )
    //endregion
}
