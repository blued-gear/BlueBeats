package apps.chocolatecakecodes.bluebeats.database.dao.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.TagFields
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagReferenceEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagTypeEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.ID3TagValueEntity
import apps.chocolatecakecodes.bluebeats.util.Utils

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

    fun getFilesWithAnyTag(type: String): List<Pair<MediaFile, String>> {
        return findFilesWithAnyTag(type).map {
            mediaFileDao.getForId(it.file_id) to it.value_str
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
        val existingTags = findTagsForFile(file.id)

        Utils.diffChanges(
            existingTags.map { it.type_str }.toSet(),
            currentTags.keys
        ).let { (added, deleted, unchanged) ->
            added.forEach {
                getTypeIdOrInsert(it).let { typeId ->
                    getValueIdOrInsert(typeId, currentTags[it]!!).let { valId ->
                        insertValueRefEntity(ID3TagReferenceEntity(0, valId, file.id))
                    }
                }
            }

            unchanged.forEach { type ->
                // if values are not equal then update them by replacing the entries
                if(currentTags[type]!! != existingTags.find { it.type_str == type }!!.value_str){
                    val typeId = existingTags.find { it.type_str == type }!!.type_id
                    val valId = existingTags.find { it.type_str == type }!!.value_id

                    listOf(valId).let {
                        deleteValueRefsForFile(file.id, it)
                        if(!isValueReferenced(valId, file.id))
                            deleteValueEntities(it)
                    }

                    getValueIdOrInsert(typeId, currentTags[type]!!).let { newValId ->
                        insertValueRefEntity(ID3TagReferenceEntity(0, newValId, file.id))
                    }
                }
            }

            deleted.map { toDelete ->
                existingTags.find {
                    it.type_str == toDelete
                }!!
            }.also {
                deleteValueRefsForFile(file.id, it.map { it.value_id })
                cleanupTagValues(it, file.id)
            }
        }
    }

    @Transaction
    open fun deleteAllEntriesOfFile(file: MediaFile) {
        findTagsForFile(file.id).let {
            deleteValueRefsForFile(file.id, it.map { it.value_id })
            cleanupTagValues(it, file.id)
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

    @Query("SELECT type.str as type_str, val.str as value_str, ref.file as file_id " +
            "FROM ID3TagReferenceEntity AS ref " +
            "INNER JOIN ID3TagValueEntity val ON ref.tag = val.id " +
            "INNER JOIN ID3TagTypeEntity AS type ON val.type = type.id " +
            "WHERE type.str = :type;")
    protected abstract fun findFilesWithAnyTag(type: String): List<FileTag2>

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

        tags.title?.let { ret.put("title", it) }
        tags.artist?.let { ret.put("artist", it) }
        tags.genre?.let { ret.put("genre", it) }
        if(tags.length > 0)
            ret.put("length", tags.length.toString())

        return ret
    }

    private fun mapToTagFields(tags: Map<String, String>): TagFields {
        return TagFields(
            title = tags["title"],
            artist = tags["artist"],
            genre = tags["genre"],
            length = tags["length"]?.toLong() ?: 0
        )
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
    protected data class FileTag2(val type_str: String, val value_str: String, val file_id: Long)
}
