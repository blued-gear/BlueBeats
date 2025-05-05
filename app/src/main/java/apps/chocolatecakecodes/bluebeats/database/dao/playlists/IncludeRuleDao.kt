package apps.chocolatecakecodes.bluebeats.database.dao.playlists

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.IncludeRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleDirEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleEntity
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.IncludeRuleFileEntry
import apps.chocolatecakecodes.bluebeats.database.entity.playlists.ShareEmbed
import apps.chocolatecakecodes.bluebeats.util.Utils

@Dao
internal abstract class IncludeRuleDao {

    private val fileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }
    private val dirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }

    //region api

    @Transaction
    open fun createNew(initialShare: Rule.Share): IncludeRule {
        val id = insertEntity(IncludeRuleEntity(0, ShareEmbed(initialShare)))
        return IncludeRule(id, true, initialShare = initialShare)
    }

    fun load(id: Long): IncludeRule {
        val entity = getEntity(id)

        val fileEntries = getFileEntriesForRule(id).mapNotNull {
            try {
                fileDao.getForId(it.file)
            } catch(e: Exception) {
                Log.e("IncludeRuleDao", "exception while loading file", e)
                null
            }
        }.toSet()
        val dirEntries = getDirEntriesForRule(id).mapNotNull {
            try {
                dirDao.getForId(it.dir) to it.deep
            } catch(e: Exception) {
                Log.e("IncludeRuleDao", "exception while loading dir", e)
                null
            }
        }.toSet()

        return IncludeRule(id, true, dirEntries, fileEntries, entity.share.toShare())
    }

    @Transaction
    open fun save(rule: IncludeRule) {
        if(!rule.isOriginal)
            throw IllegalArgumentException("only original rules may be saved to DB")

        val currentFileEntries = generateFileEntries(rule).toSet()
        val storedFileEntries = getFileEntriesForRule(rule.id)
            .map { it.copy(id = 0) }// set id to 0 to be comparable to generated entries
            .toSet()

        Utils.diffChanges(storedFileEntries, currentFileEntries).let { (added, deleted, _) ->
            deleted.forEach {
                deleteFileEntry(it.rule, it.file)
            }
            added.forEach {
                insertFileEntry(it)
            }
        }

        val currentDirEntries = generateDirEntries(rule).toSet()
        val storedDirEntries = getDirEntriesForRule(rule.id)
            .map { it.copy(id = 0) }// set id to 0 to be comparable to generated entries
            .toSet()

        Utils.diffChanges(storedDirEntries, currentDirEntries).let { (added, deleted, _) ->
            deleted.forEach {
                deleteDirEntry(it.rule, it.dir)
            }
            added.forEach {
                insertDirEntry(it)
            }
        }

        updateEntity(IncludeRuleEntity(rule.id, ShareEmbed(rule.share)))
    }

    fun delete(rule: IncludeRule) {
        delete(rule.id)
    }

    @Transaction
    open fun delete(id: Long) {
        deleteAllFileEntries(id)
        deleteAllDirEntries(id)
        deleteEntity(id)
    }
    //endregion

    //region sql

    @Insert
    protected abstract fun insertEntity(entity: IncludeRuleEntity): Long

    @Update
    protected abstract fun updateEntity(entity: IncludeRuleEntity)

    @Query("DELETE FROM IncludeRuleEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)

    @Query("SELECT * FROM IncludeRuleEntity WHERE id = :id;")
    protected abstract fun getEntity(id: Long): IncludeRuleEntity

    @Insert
    protected abstract fun insertFileEntry(entry: IncludeRuleFileEntry): Long

    @Query("DELETE FROM IncludeRuleFileEntry WHERE rule = :rule;")
    protected abstract fun deleteAllFileEntries(rule: Long)

    @Query("DELETE FROM IncludeRuleFileEntry WHERE rule = :rule AND file = :file;")
    protected abstract fun deleteFileEntry(rule: Long, file: Long)

    @Query("SELECT * FROM IncludeRuleFileEntry WHERE rule = :rule;")
    protected abstract fun getFileEntriesForRule(rule: Long): List<IncludeRuleFileEntry>

    @Insert
    protected abstract fun insertDirEntry(entry: IncludeRuleDirEntry): Long

    @Query("DELETE FROM IncludeRuleDirEntry WHERE rule = :rule;")
    protected abstract fun deleteAllDirEntries(rule: Long)

    @Query("DELETE FROM IncludeRuleDirEntry WHERE rule = :rule AND dir = :dir;")
    protected abstract fun deleteDirEntry(rule: Long, dir: Long)

    @Query("SELECT * FROM IncludeRuleDirEntry WHERE rule = :rule;")
    protected abstract fun getDirEntriesForRule(rule: Long): List<IncludeRuleDirEntry>
    //endregion

    //region private helpers

    private fun generateFileEntries(rule: IncludeRule): List<IncludeRuleFileEntry> {
        return rule.getFiles().map {
            IncludeRuleFileEntry(
                0,
                rule.id,
                it.id
            )
        }
    }

    private fun generateDirEntries(rule: IncludeRule): List<IncludeRuleDirEntry> {
        return rule.getDirs().map {
            IncludeRuleDirEntry(
                0,
                rule.id,
                it.first.id,
                it.second
            )
        }
    }
    //endregion
}
