package apps.chocolatecakecodes.bluebeats.media.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

abstract class MediaNode : Comparable<MediaNode> {

    companion object{
        const val NULL_PARENT_ID: Long = -1
        const val UNALLOCATED_NODE_ID: Long = 0
        /**
         * used for single parsed files
         */
        val UNSPECIFIED_DIR: MediaDir = MediaDir(-2, "~UNSPECIFIED~", { null })

        @JvmStatic
        protected val NODE_CACHE_TIME = TimeUnit.MINUTES.toMillis(5)
    }

    abstract val name: String
    abstract val path: String
    abstract val parent: MediaNode?// only for runtime-caching

    private var hash: Int = -1

    init {
        // trigger caching of hashCode (else it might happen that a DB-query in the UI-Thread gets executed)
        CoroutineScope(Dispatchers.IO).launch {
            delay(50)
            this@MediaNode.hashCode()
        }
    }

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

        return compareStringNaturally(this.name, other.name)
    }

    private fun compareStringNaturally(o1: String, o2: String): Int {
        if(o1 === o2)
            return 0

        val lcCmp = o1.lowercase().compareTo(o2.lowercase())
        if(lcCmp != 0)
            return lcCmp

        return o1.compareTo(o2)
    }
}
