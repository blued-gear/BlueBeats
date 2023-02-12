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
            it.type_str to it.value_str
        }.let {
            mapToTagFields(it)
        }
    }

    fun getFilesWithTag(type: String, value: String): List<MediaFile> {
        return findFilesWithTag(type, value).map {
            mediaFileDao.getForId(it)
        }
    }

    @Query("SELECT DISTINCT str FROM ID3TagTypeEntity;")
    abstract fun getAllTagTypes(): List<String>

    @Query("SELECT DISTINCT val_e.str " +
            "FROM ID3TagTypeEntity AS type_e INNER JOIN ID3TagValueEntity as val_e ON type_e.id = val_e.type " +
            "WHERE type_e.str = :type;")
    abstract fun getAllTypeValues(type: String): List<String>

    @Transaction
    open fun saveTagOfFile(file: MediaFile) {//TODO there should be a test for this
        val currentTags = tagFieldsToMap(file.mediaTags)
        val existingTags = findTagsForFile(file.entityId)

        Utils.diffChanges(
            existingTags.map { it.type_str }.toSet(),
            currentTags.keys
        ).let { (added, deleted, unchanged) ->
            added.forEach {
                getTypeIdOrInsert(it).let { typeId ->
                    getValueIdOrInsert(typeId, currentTags[it]!!).let { valId ->
                        insertValueRefEntity(ID3TagReferenceEntity(0, valId, file.entityId))
                    }
                }
            }

            unchanged.forEach { type ->
                // if values are not equal then update them by replacing the entries
                if(currentTags[type]!! != existingTags.find { it.type_str == type }!!.value_str){
                    val typeId = existingTags.find { it.type_str == type }!!.type_id
                    val valId = existingTags.find { it.type_str == type }!!.value_id

                    listOf(valId).let {
                        deleteValueRefsForFile(file.entityId, it)
                        if(!isValueReferenced(valId, file.entityId))
                            deleteValueEntities(it)
                    }

                    getValueIdOrInsert(typeId, currentTags[type]!!).let { newValId ->
                        insertValueRefEntity(ID3TagReferenceEntity(0, newValId, file.entityId))
                    }
                }
            }

            deleted.map { toDelete ->
                existingTags.find {
                    it.type_str == toDelete
                }!!
            }.also {
                deleteValueRefsForFile(file.entityId, it.map { it.value_id })
                cleanupTagValues(it, file.entityId)
            }
        }
    }

    @Transaction
    open fun deleteAllEntriesOfFile(file: MediaFile) {
        findTagsForFile(file.entityId).let {
            deleteValueRefsForFile(file.entityId, it.map { it.value_id })
            cleanupTagValues(it, file.entityId)
        }
    }
    //endregion

    //region db actions
    @Query("SELECT type.str AS type_str, type.id AS type_id, val.str AS value_str, val.id AS value_id " +
            "FROM ID3TagReferenceEntity AS ref " +
            "INNER JOIN ID3TagValueEntity AS val ON ref.tag = val.id " +
            "INNER JOIN ID3TagTypeEntity AS type ON val.type = type.id " +
            "WHERE ref.file = :file;")
    protected abstract fun findTagsForFile(file: Long): List<FileTag>

    @Query("SELECT file " +
            "FROM ID3TagReferenceEntity AS ref " +
            "INNER JOIN ID3TagValueEntity val ON ref.tag = val.id " +
            "INNER JOIN ID3TagTypeEntity AS type ON val.type = type.id " +
            "WHERE type.str = :type AND val.str = :value;")
    protected abstract fun findFilesWithTag(type: String, value: String): List<Long>

    @Query("SELECT id FROM ID3TagTypeEntity WHERE str = :typeValue;")
    protected abstract fun findTypeIdForString(typeValue: String): Long?

    @Insert
    protected abstract fun insertTypeEntity(entity: ID3TagTypeEntity): Long

    @Query("DELETE FROM ID3TagTypeEntity WHERE id IN (:ids);")
    protected abstract fun deleteTypeEntities(ids: List<Long>)

    @Query("SELECT id FROM ID3TagValueEntity WHERE type = :type AND str = :value;")
    protected abstract fun findValueIdForString(type: Long, value: String): Long?

    @Query("SELECT EXISTS (SELECT id FROM ID3TagValueEntity WHERE type = :type AND id != :exceptForValue);")
    protected abstract fun isTypeReferenced(type: Long, exceptForValue: Long): Boolean

    @Insert
    protected abstract fun insertValueEntity(entity: ID3TagValueEntity): Long

    @Query("DELETE FROM ID3TagValueEntity WHERE id IN (:ids);")
    protected abstract fun deleteValueEntities(ids: List<Long>)

    @Query("SELECT tag FROM ID3TagReferenceEntity WHERE file = :file;")
    protected abstract fun findValueRefsForFile(file: Long): List<Long>

    @Query("SELECT EXISTS (SELECT id FROM ID3TagReferenceEntity WHERE tag = :value AND file != :exceptForFile);")
    protected abstract fun isValueReferenced(value: Long, exceptForFile: Long): Boolean

    @Insert
    protected abstract fun insertValueRefEntity(entity: ID3TagReferenceEntity)

    @Query("DELETE FROM ID3TagReferenceEntity WHERE file = :file AND tag IN (:tags);")
    protected abstract fun deleteValueRefsForFile(file: Long, tags: List<Long>)
    //endregion

    //region private methods
    private fun tagFieldsToMap(tags: TagFields): Map<String, String> {
        val ret = HashMap<String, String>()

        if(!tags.title.isNullOrEmpty())
            ret.put("title", tags.title)
        if(!tags.artist.isNullOrEmpty())
            ret.put("artist", tags.artist)
        if(!tags.genre.isNullOrEmpty())
            ret.put("genre", tags.genre)
        if(tags.length > 0)
            ret.put("length", tags.length.toString())

        return ret
    }

    private fun mapToTagFields(tags: Map<String, String>): TagFields {
        val ret = TagFields()

        tags["title"]?.let { ret.title = it }
        tags["artist"]?.let { ret.artist = it }
        tags["genre"]?.let { ret.genre = it }
        tags["length"]?.let { ret.length = it.toLong() }

        return ret
    }

    private fun cleanupTagValues(values: List<FileTag>, fileId: Long) {
        values.filterNot {// deleted unused values
            isValueReferenced(it.value_id, fileId)
        }.also {
            deleteValueEntities(it.map { it.value_id })
        }.filterNot { // deleted unused types
            isTypeReferenced(it.type_id, it.value_id)
        }.also {
            deleteTypeEntities(it.map { it.type_id })
        }
    }

    private fun getTypeIdOrInsert(str: String): Long {
        return findTypeIdForString(str)
            ?: insertTypeEntity(ID3TagTypeEntity(0, str))
    }

    private fun getValueIdOrInsert(type: Long, str: String): Long {
        return findValueIdForString(type, str)
            ?: insertValueEntity(ID3TagValueEntity(0, type, str))
    }
    //endregion

    protected data class FileTag(val type_str: String, val type_id: Long, val value_str: String, val value_id: Long)
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
