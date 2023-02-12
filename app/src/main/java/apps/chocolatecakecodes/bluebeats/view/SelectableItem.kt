package apps.chocolatecakecodes.bluebeats.view

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.SimpleObservable
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import java.util.*

internal abstract class SelectableItem<Holder : RecyclerView.ViewHolder> : AbstractItem<Holder>() {

    private val selectedObservable = SimpleObservable<Boolean>(super.isSelected)

    override var isSelected: Boolean
        get() = selectedObservable.get()
        set(value) {
            selectedObservable.set(value)
        }

    abstract class ViewHolder<Item : SelectableItem<*>>(view: View) : FastAdapter.ViewHolder<Item>(view) {

        private var selectedObserver: Observer? = null

        // must be called from subclasses
        override fun bindView(item: Item, payloads: List<Any>) {
            assert(selectedObserver === null) { "Holder was not unbound correctly" }

            selectedObserver = item.selectedObservable.addObserverCallback { _, selected ->
                setSelected(selected)
            }

            setSelected(item.isSelected)
        }

        // must be called from subclasses
        override fun unbindView(item: Item) {
            item.selectedObservable.deleteObserver(selectedObserver)
            selectedObserver = null

            setSelected(false)
        }

        open fun setSelected(selected: Boolean) {
            if(selected)
                this.itemView.setBackgroundResource(R.color.selection_highlight)
            else
                this.itemView.setBackgroundResource(R.color.design_default_color_background)
        }
    }
}
