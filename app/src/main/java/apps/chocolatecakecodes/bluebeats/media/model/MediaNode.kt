package apps.chocolatecakecodes.bluebeats.media.model

import java.util.*
import java.util.concurrent.TimeUnit

abstract class MediaNode {

    companion object{
        val NULL_PARENT_ID: Long = -1
        val UNALLOCATED_NODE_ID: Long = 0
        /**
         * used for single parsed files
         */
        val UNSPECIFIED_DIR: MediaDir = MediaDir(MediaDirEntity(-2, "~UNSPECIFIED~", NULL_PARENT_ID))

        @JvmStatic
        protected val NODE_CACHE_TIME = TimeUnit.MINUTES.toMillis(5)
    }

    abstract val name: String
    abstract val path: String
    abstract val parent: MediaNode?// only be for runtime-caching

    abstract override fun equals(other: Any?): Boolean

    override fun toString(): String {
        return "${this.javaClass.simpleName}: $path"
    }
    override fun hashCode(): Int {
        return Objects.hash(path, this.javaClass.canonicalName)
    }
}