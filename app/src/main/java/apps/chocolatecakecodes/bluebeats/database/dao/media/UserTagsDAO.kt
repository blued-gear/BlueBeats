package apps.chocolatecakecodes.bluebeats.database.dao.media

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.media.UserTagEntity
import apps.chocolatecakecodes.bluebeats.database.entity.media.UserTagRelation

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
        val existingTags = getUserTags(file.id).toHashSet()
        val existingTagNames = existingTags.map {
            it.name
        }.toHashSet()

        // save all missing tags
        tagsSet.minus(existingTagNames).forEach {
            saveUserTag(UserTagEntity(MediaNode.UNALLOCATED_NODE_ID, it))
        }

        // delete relations for removed tags
        existingTags.filter {
            !tagsSet.contains(it.name)
        }.forEach {
            removeRelation(it.id, file.id)
        }

        // re-save relations (to update positions)
        tags.map {
            getUserTagEntityForNames(listOf(it)).first()
        }.mapIndexed { pos, entity ->
            UserTagRelation(MediaNode.UNALLOCATED_NODE_ID, entity.id, file.id, pos)
        }.forEach {
            saveUserTagRelation(it)
        }
    }

    fun deleteUserTagsOfFile(file: MediaFile) {
        removeRelationsForFile(file.id)
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

    @Insert(onConflict = REPLACE)
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
