package apps.chocolatecakecodes.bluebeats.view.specialitems

import android.view.View
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import com.mikepenz.fastadapter.drag.IDraggable

internal class MediaFileItem(
    val file: MediaFile,
    draggable: Boolean = false
) : SelectableItem<MediaFileItem.ViewHolder>(), IDraggable {

    override val type: Int = MediaFileItem::class.hashCode()
    override val layoutRes: Int = R.layout.view_media_node

    override val isDraggable: Boolean = draggable

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<MediaFileItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mf_text)
        private val dragHandle: View = view.findViewById(R.id.v_mf_handle)

        override fun bindView(item: MediaFileItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            title.text = item.file.name
            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: MediaFileItem) {
            super.unbindView(item)

            title.text = null
        }
    }
}
