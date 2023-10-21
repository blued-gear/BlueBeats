package apps.chocolatecakecodes.bluebeats.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.core.view.doOnNextLayout
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class ThumbSetterHelper(
    private val target: ImageView,
    backgroundColRes: Int = R.color.gray_410,
    foregroundColRes: Int = R.color.gray_600
) {

    private val svgColor = ColorStateList.valueOf(target.context.getColor(foregroundColRes))
    private var jobScope: CoroutineScope? = null

    init {
        target.setBackgroundColor(target.context.getColor(backgroundColRes))
    }

    fun setThumb(showThumb: Boolean, file: MediaFile) {
        if(showThumb) {
            target.visibility = View.VISIBLE

            target.imageTintList = svgColor
            target.setImageResource(R.drawable.ic_baseline_access_time_24)

            if(target.width == 0 || target.height == 0){
                target.doOnNextLayout {
                    loadThumbnail(file)
                }
            } else {
                loadThumbnail(file)
            }
        } else {
            target.visibility = View.GONE
        }
    }

    /**
     * cancels loading of the thumb, if currently running
     */
    fun cancel() {
        synchronized(this) {
            jobScope?.cancel()
        }
    }

    private fun loadThumbnail(file: MediaFile) {
        if(target.height <= 0)
            return

        synchronized(this) {
            cancel()

            jobScope = CoroutineScope(Dispatchers.IO).also {
                it.launch {
                    if (File(file.path).exists()) {
                        VlcManagers.getMediaDB().getSubject()
                            .getThumbnail(file, -1, target.height).let {
                                withContext(Dispatchers.Main) {
                                    if (it !== null) {
                                        target.imageTintList = null
                                        target.setImageBitmap(it)
                                    } else {
                                        when (file.type) {
                                            MediaFile.Type.AUDIO -> target.setImageResource(R.drawable.ic_baseline_audiotrack_24)
                                            MediaFile.Type.VIDEO -> target.setImageResource(R.drawable.ic_baseline_local_movies_24)
                                            MediaFile.Type.OTHER -> target.setImageResource(R.drawable.ic_baseline_insert_drive_file_24)
                                        }
                                    }
                                }
                            }
                    } else {
                        withContext(Dispatchers.Main) {
                            target.setImageResource(R.drawable.ic_baseline_file_removed)
                        }
                    }
                }
            }
        }
    }
}