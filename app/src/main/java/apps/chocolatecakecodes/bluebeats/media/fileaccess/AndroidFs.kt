package apps.chocolatecakecodes.bluebeats.media.fileaccess

import android.content.Context
import android.os.Environment
import java.io.File

class AndroidFs : FileEnumerator {

    private var scanRoots: Set<String>? = null

    override suspend fun visitAllFiles(ctx: Context, visitor: suspend (String) -> Unit) {
        val roots = synchronized(this) {
            if(scanRoots == null)
                scanRoots = listMediaRoots(ctx)
            scanRoots!!
        }

        roots.forEach {
            walkTree(File(it), visitor)
        }
    }

    private fun listMediaRoots(ctx: Context): Set<String> {
        val roots = HashSet<String>()

        val internalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        roots.add(internalStoragePath)

        // https://stackoverflow.com/a/70879069/8288367
        ctx.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull {
                val nameSubPos = it.absolutePath.lastIndexOf("/Android/data")
                if(nameSubPos < 1)
                    return@mapNotNull null

                it.absolutePath.substring(0, nameSubPos)
            }.filter {
                it != internalStoragePath
            }.let {
                roots.addAll(it)
            }

        return roots
    }

    private suspend fun walkTree(start: File, visitor: suspend (String) -> Unit) {
        start.listFiles()?.partition {
            it.isDirectory
        }?.let { (dirs, files) ->
            files.forEach {
                visitor(it.absolutePath)
            }

            dirs.forEach {
                walkTree(it, visitor)
            }
        }
    }
}