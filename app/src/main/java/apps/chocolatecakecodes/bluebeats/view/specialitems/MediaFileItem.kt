package apps.chocolatecakecodes.bluebeats.view.specialitems

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.ThumbSetterHelper
import com.mikepenz.fastadapter.drag.IDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    //override var identifier: Long = file.hashCode().toLong()

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<MediaFileItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mf_text)
        private val dragHandle: View = view.findViewById(R.id.v_mf_handle)
        private val thumb: ImageView = view.findViewById(R.id.v_mf_thumb)

        private val thumbHelper = ThumbSetterHelper(thumb)

        override fun bindView(item: MediaFileItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            setTitle(item)
            setThumb(item)

            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: MediaFileItem) {
            super.unbindView(item)

            thumbHelper.cancel()

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
            thumbHelper.setThumb(item.showThumb, item.file)
        }
    }
}
