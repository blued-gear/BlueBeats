package apps.chocolatecakecodes.bluebeats.view.specialitems.playlistitems

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.playlist.items.TimeSpanItem
import apps.chocolatecakecodes.bluebeats.util.ThumbSetterHelper
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.view.specialitems.SelectableItem
import com.mikepenz.fastadapter.drag.IDraggable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TimeSpanItemItem(
    override val item: TimeSpanItem,
    override val isDraggable: Boolean = false
) : SelectableItem<TimeSpanItemItem.ViewHolder>(), IDraggable,
    PlaylistItemItem<TimeSpanItemItem.ViewHolder> {

    override val type: Int = TimeSpanItemItem::class.hashCode()
    override val layoutRes: Int = R.layout.view_pl_timespan_item

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<TimeSpanItemItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_tsitm_title)
        private val time: TextView = view.findViewById(R.id.v_tsitm_time)
        private val dragHandle: View = view.findViewById(R.id.v_tsitm_handle)
        private val thumb: ImageView = view.findViewById(R.id.v_tsitm_thumb)

        private val thumbHelper = ThumbSetterHelper(thumb)

        override fun bindView(item: TimeSpanItemItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            val model = item.item

            setTitle(model)
            setTime(model)
            setThumb(model)

            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: TimeSpanItemItem) {
            super.unbindView(item)

            thumbHelper.cancel()

            title.text = null
            time.text = null
            thumb.setImageBitmap(null)
        }

        private fun setTitle(item: TimeSpanItem) {
            title.text = super.itemView.context.getString(R.string.misc_loading)

            CoroutineScope(Dispatchers.IO).launch {
                val text = item.file.title

                withContext(Dispatchers.Main) {
                    title.text = text
                }
            }
        }

        @SuppressLint("SetTextI18n")
        private fun setTime(item: TimeSpanItem) {
            val withHours = (item.endMs / (60 * 60 * 1000)) > 0
            val startTime = Utils.formatTime(item.startMs, withHours)
            val endTime = Utils.formatTime(item.endMs, withHours)

            time.text = "$startTime - $endTime"
        }

        private fun setThumb(item: TimeSpanItem) {
            thumbHelper.setThumb(true, item.file)
        }
    }
}
