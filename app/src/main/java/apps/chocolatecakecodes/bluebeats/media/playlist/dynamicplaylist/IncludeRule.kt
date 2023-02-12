package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.Dao
import androidx.room.Transaction
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

internal class IncludeRule private constructor(
    private val entityId: Long,
    dirs: Set<DirPathInclude> = emptySet(),
    files: Set<MediaFile> = emptySet(),
    initialShare: Rule.Share
) : Rule {

    private val dirs = HashMap<MediaDir, Boolean>(dirs.associate { it.first to it.second })
    private val files = HashSet(files)
    private val filesRO: Set<MediaFile> by lazy {
        Collections.unmodifiableSet(this.files)
    }

    override var share = initialShare

    override fun generateItems(amount: Int, exclude: ExcludeRule): List<MediaFile> {
        return expandDirs()
            .union(getFiles())
            .filterNot(exclude::isExcluded)
            .shuffled()
            .take(amount)
            .toList()
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

    @Dao
    internal abstract class IncludeRuleDao {

        @Transaction
        open fun createNew(): IncludeRule {
            TODO()
        }

        fun load(id: Long): IncludeRule {
            TODO()
        }

        @Transaction
        open fun save(rule: IncludeRule) {
            TODO()
        }

        @Transaction
        open fun delete(rule: IncludeRule) {
            TODO()
        }

        fun getEntityId(rule: IncludeRule): Long {
            return rule.entityId
        }

        //TODO
    }
}

//region entities

//endregion
