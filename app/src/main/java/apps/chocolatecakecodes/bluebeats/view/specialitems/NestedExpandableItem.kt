package apps.chocolatecakecodes.bluebeats.view.specialitems

import androidx.annotation.CallSuper
import androidx.core.view.updatePadding
import apps.chocolatecakecodes.bluebeats.R
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem

internal abstract class NestedExpandableItem<VH : FastAdapter.ViewHolder<*>>(
    level: Int = 0,
    private val withBorder: Boolean = false,
    private val inset: Int = DEFAULT_INSET
) : AbstractExpandableItem<VH>() {

    companion object {
        const val DEFAULT_INSET: Int = 40
    }

    var level: Int = level
        private set

    protected fun addSubItem(item: ISubItem<*>) {
        if(item is NestedExpandableItem)
            item.level = level + 1
        subItems.add(item)
    }

    protected fun addSubItems(items: List<ISubItem<*>>) {
        items.onEach {
            if(it is NestedExpandableItem)
                it.level = level + 1
        }.let {
            subItems.addAll(it)
        }
    }

    @CallSuper
    override fun bindView(holder: VH, payloads: List<Any>) {
        holder.itemView.apply {
            if(withBorder)
                setBackgroundResource(R.drawable.shape_border)
            updatePadding(left = level * inset)
        }

        super.bindView(holder, payloads)
    }

    @CallSuper
    override fun unbindView(holder: VH) {
        holder.itemView.apply {
            if(withBorder)
                setBackgroundResource(0)
            updatePadding(left = 0)
        }

        super.unbindView(holder)
    }
}
