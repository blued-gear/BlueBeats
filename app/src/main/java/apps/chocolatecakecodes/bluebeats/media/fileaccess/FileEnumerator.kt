package apps.chocolatecakecodes.bluebeats.media.fileaccess

import android.content.Context

interface FileEnumerator {

    fun visitAllFiles(ctx: Context, visitor: (String) -> Unit)
}