package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.Dao
import androidx.room.Transaction
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

        @Transaction
        open fun createNew(): ExcludeRule {
            TODO()
        }

        fun load(id: Long): ExcludeRule {
            TODO()
        }

        @Transaction
        open fun save(rule: ExcludeRule) {
            TODO()
        }

        @Transaction
        open fun delete(rule: ExcludeRule) {
            TODO()
        }

        fun getEntityId(rule: ExcludeRule): Long {
            return rule.entityId
        }

        //TODO
    }
}

//region entities

//endregion
