package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.util.Utils
import java.util.Objects
import java.util.concurrent.TimeUnit

internal abstract class MediaNode : Comparable<MediaNode> {

    companion object {
        @JvmStatic
        protected val NODE_CACHE_TIME = TimeUnit.MINUTES.toMillis(5)

        const val NULL_PARENT_ID: Long = -1
        const val UNALLOCATED_NODE_ID: Long = 0
        /**
         * used for single parsed files
         */
        val UNSPECIFIED_DIR: MediaDir = MediaDir.new(-2, "~UNSPECIFIED~", { null })
        val INVALID_FILE: MediaFile = MediaFile.new(-2, "~Missing File~", MediaFile.Type.OTHER, { UNSPECIFIED_DIR })
    }

    abstract val name: String
    abstract val path: String
    abstract val parent: MediaNode?// only for runtime-caching

    private var hash: Int = -1

    abstract override fun equals(other: Any?): Boolean

    override fun toString(): String {
        return "${this.javaClass.simpleName}: $path"
    }
    override fun hashCode(): Int {
        if(hash == -1)
            hash = Objects.hash(this.javaClass.canonicalName, path)
        return hash
    }

    override fun compareTo(other: MediaNode): Int {
        // dirs before files
        if(this is MediaDir && other !is MediaDir)
            return -1
        if(this !is MediaDir && other is MediaDir)
            return 1

        return Utils.compareStringNaturally(this.name, other.name)
    }
}
