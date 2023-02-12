package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.database.MediaDirEntity
import apps.chocolatecakecodes.bluebeats.database.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/** <dir, deep (include all subdirs)> */
internal typealias DirPathInclude = Pair<MediaDir, Boolean>

internal class ExcludeRule private constructor(
    private val entityId: Long,
    dirs: Set<DirPathInclude> = emptySet(), files: Set<MediaFile> = emptySet()
) {

    companion object {
        val EMPTY_EXCLUDE get() = ExcludeRule(-1)

        fun temporaryExclude(dirs: Set<DirPathInclude> = emptySet(), files: Set<MediaFile> = emptySet()): ExcludeRule {
            return ExcludeRule(-1, dirs, files)
        }
    }

    private val dirs = HashMap<MediaDir, Boolean>(dirs.associate { it.first to it.second })
    private val files = HashSet(files)
    private val filesRO: Set<MediaFile> by lazy {
        Collections.unmodifiableSet(this.files)
    }

    fun getDirs(): Set<DirPathInclude> {
        return dirs.map {
            DirPathInclude(it.key, it.value )
        }.toSet()
    }

    fun getFiles() = filesRO

    /**
     * Adds a dir to the exclude list.
     * If was already added, its former value for deep will be overwritten
     * @param dir the dir to exclude
     * @param deep if true the dir and all its subdirs will be excluded
     */
    fun addDir(dir: MediaDir, deep: Boolean) {
        dirs.put(dir, deep)
    }

    fun removeDir(dir: MediaDir) {
        dirs.remove(dir)
    }

    fun addFile(file: MediaFile) {
        files.add(file)
    }

    fun removeFile(file: MediaFile) {
        files.remove(file)
    }

    /**
     * returns true if the given file is matched by any of the excluded paths
     */
    fun isExcluded(file: MediaFile): Boolean {
        if(files.contains(file))
            return true

        val fileParentPath = file.parent.path
        dirs.forEach { (dir, deep) ->
            if(fileParentPath == dir.path
                || (deep && fileParentPath.startsWith(dir.path)))
                return true
        }

        return false
    }

    /**
     * returns an ExcludeRule-instance which excludes all paths from this and other
     * (if both define an entry for a dir then deep will be set if any one of the both has deep set to true)
     */
    fun union(other: ExcludeRule): ExcludeRule {
        val dirUnion = HashMap(this.dirs)
        other.dirs.forEach { (dir, deep) ->
            dirUnion.merge(dir, deep) { a, b ->
                a or b
            }
        }

        return ExcludeRule(
            -1,
            dirUnion.map { DirPathInclude(it.key, it.value) }.toSet(),
            this.files.union(other.files)
        )
    }

    @Dao
    internal abstract class ExcludeRuleDao {

        private val fileDao: MediaFileDAO by lazy {
            RoomDB.DB_INSTANCE.mediaFileDao()
        }
        private val dirDao: MediaDirDAO by lazy {
            RoomDB.DB_INSTANCE.mediaDirDao()
        }

        //region api

        @Transaction
        open fun createNew(): ExcludeRule {
            val id = insertEntity(ExcludeRuleEntity(0))
            return ExcludeRule(id)
        }

        fun load(id: Long): ExcludeRule {
            val fileEntries = getFileEntriesForRule(id).map {
                fileDao.getForId(it.file)
            }.toSet()
            val dirEntries = getDirEntriesForRule(id).map {
                dirDao.getForId(it.dir) to it.deep
            }.toSet()

            return ExcludeRule(id, dirEntries, fileEntries)
        }

        @Transaction
        open fun save(rule: ExcludeRule) {
            val currentFileEntries = generateFileEntries(rule).toSet()
            val storedFileEntries = getFileEntriesForRule(rule.entityId)
                .map { it.copy(id = 0) }// set id to 0 to be comparable to generated entries
                .toSet()

            storedFileEntries.minus(currentFileEntries).forEach {
                deleteFileEntry(it.rule, it.file)
            }
            currentFileEntries.minus(storedFileEntries).forEach {
                insertFileEntry(it)
            }

            val currentDirEntries = generateDirEntries(rule).toSet()
            val storedDirEntries = getDirEntriesForRule(rule.entityId)
                .map { it.copy(id = 0) }// set id to 0 to be comparable to generated entries
                .toSet()

            storedDirEntries.minus(currentDirEntries).forEach {
                deleteDirEntry(it.rule, it.dir)
            }
            currentDirEntries.minus(storedDirEntries).forEach {
                insertDirEntry(it)
            }
        }

        @Transaction
        open fun delete(rule: ExcludeRule) {
            deleteAllFileEntries(rule.entityId)
            deleteAllDirEntries(rule.entityId)
            deleteEntity(rule.entityId)
        }

        fun getEntityId(rule: ExcludeRule): Long {
            return rule.entityId
        }
        //endregion

        //region sql

        @Insert
        protected abstract fun insertEntity(entity: ExcludeRuleEntity): Long

        @Query("DELETE FROM ExcludeRuleEntity WHERE id = :id;")
        protected abstract fun deleteEntity(id: Long)

        @Insert
        protected abstract fun insertFileEntry(entry: ExcludeRuleFileEntry): Long

        @Query("DELETE FROM ExcludeRuleFileEntry WHERE rule = :rule;")
        protected abstract fun deleteAllFileEntries(rule: Long)

        @Query("DELETE FROM ExcludeRuleFileEntry WHERE rule = :rule AND file = :file;")
        protected abstract fun deleteFileEntry(rule: Long, file: Long)

        @Query("SELECT * FROM ExcludeRuleFileEntry WHERE rule = :rule;")
        protected abstract fun getFileEntriesForRule(rule: Long): List<ExcludeRuleFileEntry>

        @Insert
        protected abstract fun insertDirEntry(entry: ExcludeRuleDirEntry): Long

        @Query("DELETE FROM ExcludeRuleDirEntry WHERE rule = :rule;")
        protected abstract fun deleteAllDirEntries(rule: Long)

        @Query("DELETE FROM ExcludeRuleDirEntry WHERE rule = :rule AND dir = :dir;")
        protected abstract fun deleteDirEntry(rule: Long, dir: Long)

        @Query("SELECT * FROM ExcludeRuleDirEntry WHERE rule = :rule;")
        protected abstract fun getDirEntriesForRule(rule: Long): List<ExcludeRuleDirEntry>
        //endregion

        //region private helpers

        private fun generateFileEntries(rule: ExcludeRule): List<ExcludeRuleFileEntry> {
            return rule.getFiles().map {
                ExcludeRuleFileEntry(
                    0,
                    rule.entityId,
                    it.entity.id
                )
            }
        }

        private fun generateDirEntries(rule: ExcludeRule): List<ExcludeRuleDirEntry> {
            return rule.getDirs().map {
                ExcludeRuleDirEntry(
                    0,
                    rule.entityId,
                    it.first.entity.id,
                    it.second
                )
            }
        }
        //endregion
    }
}

//region entities
@Entity
internal data class ExcludeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ExcludeRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["rule", "file"], unique = true)
    ]
)
internal data class ExcludeRuleFileEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val file: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ExcludeRuleEntity::class,
            parentColumns = ["id"], childColumns = ["rule"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaDirEntity::class,
            parentColumns = ["id"], childColumns = ["dir"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["rule", "dir"], unique = true)
    ]
)
internal data class ExcludeRuleDirEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val dir: Long,
    val deep: Boolean
)
//endregion
