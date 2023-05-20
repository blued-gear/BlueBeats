package apps.chocolatecakecodes.bluebeats.view.specialitems

import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.SimpleObservable
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import java.lang.ref.WeakReference
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
        private var currentItem: WeakReference<Item>? = null

        // must be called from subclasses
        override fun bindView(item: Item, payloads: List<Any>) {
            if(selectedObserver !== null){
                Log.w("SelectableItem", "Holder was not unbound correctly")
                currentItem!!.get()?.selectedObservable?.deleteObserver(selectedObserver)
            }

            currentItem = WeakReference(item)
            selectedObserver = item.selectedObservable.addObserverCallback { _, selected ->
                setSelected(selected)
            }

            setSelected(item.isSelected)
        }

        // must be called from subclasses
        override fun unbindView(item: Item) {
            if(currentItem === null || currentItem!!.get() !== item) {
                Log.w("SelectableItem", "Holder::unbindView was called for wrong item")
            } else {
                currentItem!!.get()?.selectedObservable?.deleteObserver(selectedObserver)
                selectedObserver = null
                currentItem = null
            }

            setSelected(false)
        }

        open fun setSelected(selected: Boolean) {
            if(selected)
                this.itemView.setBackgroundResource(R.color.selection_highlight)
            else
                this.itemView.setBackgroundResource(com.google.android.material.R.color.design_default_color_background)
        }
    }
}
