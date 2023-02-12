package apps.chocolatecakecodes.bluebeats.view.specialitems

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import com.mikepenz.fastadapter.drag.IDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @param file the MediaFile to represent
 * @param isDraggable if the item should be draggable
 * @param useTitle if true, the title from the tags will be used instead of the filename (if available)
 */
internal open class MediaFileItem(
    val file: MediaFile,
    override val isDraggable: Boolean = false,
    private val useTitle: Boolean = false,
    private val showThumb: Boolean = false
) : SelectableItem<MediaFileItem.ViewHolder>(), IDraggable {

    override val type: Int = MediaFileItem::class.hashCode()
    override val layoutRes: Int = R.layout.view_media_node
    override var identifier: Long = file.hashCode().toLong()

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<MediaFileItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mf_text)
        private val dragHandle: View = view.findViewById(R.id.v_mf_handle)
        private val thumb: ImageView = view.findViewById<ImageView?>(R.id.v_mf_thumb).apply {
            setBackgroundColor(context.getColor(R.color.gray_410))
        }

        private val svgColor = ColorStateList.valueOf(view.context.getColor(R.color.gray_600))

        override fun bindView(item: MediaFileItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            setTitle(item)
            setThumb(item)

            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: MediaFileItem) {
            super.unbindView(item)

            title.text = null
            thumb.setImageBitmap(null)
        }

        private fun setTitle(item: MediaFileItem) {
            title.text = super.itemView.context.getString(R.string.misc_loading)

            CoroutineScope(Dispatchers.IO).launch {
                val text = if(item.useTitle)
                    item.file.title
                else
                    item.file.name

                withContext(Dispatchers.Main) {
                    title.text = text
                }
            }
        }

        private fun setThumb(item: MediaFileItem) {
            if(item.showThumb) {
                thumb.visibility = View.VISIBLE

                thumb.imageTintList = svgColor
                thumb.setImageResource(R.drawable.ic_baseline_access_time_24)

                if(thumb.width == 0 || thumb.height == 0){
                    thumb.doOnNextLayout {
                        loadThumbnail(item.file)
                    }
                } else {
                    loadThumbnail(item.file)
                }
            } else {
                thumb.visibility = View.GONE
            }
        }

        private fun loadThumbnail(file: MediaFile) {
            if(thumb.height > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    if(File(file.path).exists()) {
                        VlcManagers.getMediaDB().getSubject()
                            .getThumbnail(file, -1, thumb.height).let {
                                withContext(Dispatchers.Main) {
                                    if(it !== null) {
                                        thumb.imageTintList = null
                                        thumb.setImageBitmap(it)
                                    } else {
                                        when(file.type) {
                                            MediaFile.Type.AUDIO -> thumb.setImageResource(R.drawable.ic_baseline_audiotrack_24)
                                            MediaFile.Type.VIDEO -> thumb.setImageResource(R.drawable.ic_baseline_local_movies_24)
                                            MediaFile.Type.OTHER -> thumb.setImageResource(R.drawable.ic_baseline_insert_drive_file_24)
                                        }
                                    }
                                }
                            }
                    } else {
                        withContext(Dispatchers.Main) {
                            thumb.setImageResource(R.drawable.ic_baseline_file_removed)
                        }
                    }
                }
            }
        }
    }
}
