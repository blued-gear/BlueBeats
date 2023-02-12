package apps.chocolatecakecodes.bluebeats.view.specialitems

import android.view.View
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import com.mikepenz.fastadapter.drag.IDraggable

internal class MediaDirItem(
    val dir: MediaDir,
    draggable: Boolean = false
) : SelectableItem<MediaDirItem.ViewHolder>(), IDraggable {

    override val type: Int = MediaDirItem::class.hashCode()
    override val layoutRes: Int = R.layout.view_media_node
    override var identifier: Long = dir.hashCode().toLong()

    override val isDraggable: Boolean = draggable

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<MediaDirItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mf_text)
        private val dragHandle: View = view.findViewById(R.id.v_mf_handle)

        override fun bindView(item: MediaDirItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            title.text = item.dir.name
            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: MediaDirItem) {
            super.unbindView(item)

            title.text = null
        }
    }
}