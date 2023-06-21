package apps.chocolatecakecodes.bluebeats.media.fileaccess

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContentResolverCompat
import java.io.IOException

@RequiresApi(Build.VERSION_CODES.Q)
internal class AndroidMediaStore : FileEnumerator {

    companion object {
        private val DATA_COL = arrayOf(MediaStore.DownloadColumns.DATA)
    }

    override fun visitAllFiles(ctx: Context, visitor: (String) -> Unit) {
        visitAllFilesInCategory(
            ctx.contentResolver,
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            visitor
        )
        visitAllFilesInCategory(
            ctx.contentResolver,
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            visitor
        )
        /*visitAllFilesInCategory(
            ctx.contentResolver,
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL),
            visitor
        )*/
    }

    private fun visitAllFilesInCategory(resolver: ContentResolver, root: Uri, visitor: (String) -> Unit) {
        ContentResolverCompat.query(
            resolver,
            root,
            DATA_COL,
            null, null,
            "${DATA_COL[0]} ASC",
            null
        )?.use {  cursor ->
            val dataColIdx = cursor.getColumnIndexOrThrow(DATA_COL[0])

            while(cursor.moveToNext()) {
                val path = cursor.getString(dataColIdx)
                visitor(path)
            }
        } ?: throw IOException("unable to query media")
    }
}