package apps.chocolatecakecodes.bluebeats.media.fileaccess

import android.content.Context

interface FileEnumerator {

    suspend fun visitAllFiles(ctx: Context, visitor: suspend (String) -> Unit)
}