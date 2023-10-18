package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.room.*
import apps.chocolatecakecodes.bluebeats.database.*
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.takeOrAll
import java.io.File
import java.util.*

/** <dir, deep (include all subdirs)> */
internal typealias DirPathInclude = Pair<MediaDir, Boolean>

internal class IncludeRule private constructor(
    private val entityId: Long,
    override val isOriginal: Boolean,
    dirs: Set<DirPathInclude> = emptySet(),
    files: Set<MediaFile> = emptySet(),
    initialShare: Rule.Share
) : Rule<IncludeRule> {

    private val dirs = HashMap<MediaDir, Boolean>(dirs.associate { it.first to it.second })
    private val files = HashSet(files)
    private val filesRO: Set<MediaFile> by lazy {
        Collections.unmodifiableSet(this.files)
    }

    override var share = initialShare

    override fun generateItems(amount: Int, exclude: Set<PlaylistItem>): List<PlaylistItem> {
        val excludedFiles = exclude.mapNotNull {
            if(it is MediaFileItem)
                it.file
            else
                null
        }.toSet()

        return expandDirs()
            .union(getFiles())
            .minus(excludedFiles)
            .filter { File(it.path).exists() }
            .map { MediaFileItem(it) }
            .shuffled()
            .toList()
            .takeOrAll(amount)
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

    /**
     * Sets the deep value for the given dir.
     * If the dir did not exist, it will be added.
     */
    fun setDirDeep(dir: MediaDir, deep: Boolean) {
        dirs.put(dir, deep)
    }

    fun addFile(file: MediaFile) {
        files.add(file)
    }

    fun removeFile(file: MediaFile) {
        files.remove(file)
    }

    override fun copy(): IncludeRule {
        return IncludeRule(entityId, false, getDirs(), getFiles(), share.copy())
    }

    override fun applyFrom(other: IncludeRule) {
        dirs.clear()
        dirs.putAll(other.dirs)
        files.clear()
        files.addAll(other.files)
        share = other.share.copy()
    }

    override fun equals(other: Any?): Boolean {
        if(other !is IncludeRule)
            return false

        return this.getFiles() == other.getFiles()
                && this.getDirs() == other.getDirs()
                && this.share == other.share
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.canonicalName, getFiles(), getDirs(), share)
    }

    private fun expandDirs(): Set<MediaFile> {
        return getDirs().flatMap {
            if(it.second){
                expandDirRecursive(it.first)
            } else {
                it.first.getFiles()
            }
        }.toSet()
    }

    private fun expandDirRecursive(dir: MediaDir): Set<MediaFile> {
        return dir.getDirs()
            .flatMap(this::expandDirRecursive)
            .plus(dir.getFiles())
            .toSet()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if(isOriginal)
            throw IllegalStateException("only copies may be serialized (there must only be one original)")

        dest.writeLong(entityId)
        share.writeToParcel(dest, flags)

        dest.writeInt(dirs.size)
        dirs.forEach {
            dest.writeLong(it.key.entityId)
            Utils.parcelWriteBoolean(dest, it.value)
        }

        dest.writeInt(files.size)
        files.forEach {
            dest.writeLong(it.entityId)
        }
    }

    companion object CREATOR : Parcelable.Creator<IncludeRule> {

        override fun createFromParcel(parcel: Parcel): IncludeRule {
            return IncludeRule(
                parcel.readLong(),
                false,
                initialShare = Rule.Share.CREATOR.createFromParcel(parcel)
            ).apply {
                for(i in 0 until parcel.readInt()) {
                    dirs.put(
                        RoomDB.DB_INSTANCE.mediaDirDao().getForId(parcel.readLong()),
                        Utils.parcelReadBoolean(parcel)
                    )
                }

                for(i in 0 until parcel.readInt()) {
                    files.add(RoomDB.DB_INSTANCE.mediaFileDao().getForId(parcel.readLong()))
                }
            }
        }

        override fun newArray(size: Int): Array<IncludeRule?> {
            return arrayOfNulls(size)
        }
    }

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
            val id = insertEntity(IncludeRuleEntity(0, initialShare))
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

            return IncludeRule(id, true, dirEntries, fileEntries, entity.share)
        }

        @Transaction
        open fun save(rule: IncludeRule) {
            if(!rule.isOriginal)
                throw IllegalArgumentException("only original rules may be saved to DB")

            val currentFileEntries = generateFileEntries(rule).toSet()
            val storedFileEntries = getFileEntriesForRule(rule.entityId)
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
            val storedDirEntries = getDirEntriesForRule(rule.entityId)
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

            updateEntity(IncludeRuleEntity(rule.entityId, rule.share))
        }

        fun delete(rule: IncludeRule) {
            delete(rule.entityId)
        }

        @Transaction
        open fun delete(id: Long) {
            deleteAllFileEntries(id)
            deleteAllDirEntries(id)
            deleteEntity(id)
        }

        fun getEntityId(rule: IncludeRule): Long {
            return rule.entityId
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
                    rule.entityId,
                    it.entityId
                )
            }
        }

        private fun generateDirEntries(rule: IncludeRule): List<IncludeRuleDirEntry> {
            return rule.getDirs().map {
                IncludeRuleDirEntry(
                    0,
                    rule.entityId,
                    it.first.entityId,
                    it.second
                )
            }
        }
        //endregion
    }
}

//region entities
@Entity
internal data class IncludeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @Embedded(prefix = "share_") val share: Rule.Share
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = IncludeRuleEntity::class,
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
        Index(value = ["rule", "file"], unique = true),
        Index(value = ["file"])
    ]
)
internal data class IncludeRuleFileEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val file: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = IncludeRuleEntity::class,
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
        Index(value = ["rule", "dir"], unique = true),
        Index(value = ["dir"])
    ]
)
internal data class IncludeRuleDirEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val rule: Long,
    val dir: Long,
    val deep: Boolean
)
//endregion
