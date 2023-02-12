package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ExcludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rulelike
import apps.chocolatecakecodes.bluebeats.view.specialitems.NestedExpandableItem
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.items.AbstractItem

internal fun createEditor(item: Rulelike): AbstractExpandableItem<*> {
    return when(item) {
        is RuleGroup -> DynplaylistGroupEditor(item)
        is ExcludeRule -> DynplaylistExcludeEditor(item)
        is IncludeRule -> DynplaylistIncludeEditor(item)
        else -> throw IllegalArgumentException("unsupported rule")
    }
}

//region editors
internal class DynplaylistGroupEditor(val group: RuleGroup) : NestedExpandableItem<DynplaylistGroupEditor.ViewHolder>(withBorder = true) {

    override val type: Int = RuleGroup::class.hashCode()
    override val layoutRes: Int = -1

    override var isSelectable: Boolean
        get() = false
        set(_) {}

    init {
        listRules()
    }

    override var isExpanded: Boolean
        get() = super.isExpanded
        set(value) {
            super.isExpanded = value
        }

    private fun listRules() {
        addSubItems(group.getExcludes().map(::createEditor))
        addSubItems(group.getRules().map(::createEditor))
    }

    override fun createView(ctx: Context, parent: ViewGroup?): View {
        return SimpleAddableRuleContentView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    internal class ViewHolder(view: View) : FastAdapter.ViewHolder<DynplaylistGroupEditor>(view) {

        override fun bindView(item: DynplaylistGroupEditor, payloads: List<Any>) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.title.text = "Group"//TODO rules should have names
            view.addBtn.setOnClickListener {
                onAddRule()
            }
        }

        override fun unbindView(item: DynplaylistGroupEditor) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.addBtn.setOnContextClickListener(null)
        }

        private fun onAddRule() {
            //TODO show dlg with spinner
            Toast.makeText(this.itemView.context, "DynplaylistGroupEditor::add clicked", Toast.LENGTH_SHORT).show()
        }
    }
}

internal class DynplaylistExcludeEditor(val rule: ExcludeRule) : NestedExpandableItem<DynplaylistExcludeEditor.ViewHolder>(withBorder = true) {

    override val type: Int = ExcludeRule::class.hashCode()
    override val layoutRes: Int = -1

    override var isSelectable: Boolean
        get() = false
        set(_) {}

    init {
        listEntries()
    }

    private fun listEntries() {
        addSubItems(rule.getDirs().map { MediaNodeElement(it.first) })
        addSubItems(rule.getFiles().map { MediaNodeElement(it) })
    }

    override fun createView(ctx: Context, parent: ViewGroup?): View {
        return SimpleAddableRuleContentView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    internal class ViewHolder(view: View) : FastAdapter.ViewHolder<DynplaylistExcludeEditor>(view) {

        override fun bindView(item: DynplaylistExcludeEditor, payloads: List<Any>) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.title.text = "Exclude"//TODO rules should have names
            view.addBtn.setOnClickListener {
                onAddEntry()
            }
        }

        override fun unbindView(item: DynplaylistExcludeEditor) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.addBtn.setOnContextClickListener(null)
        }

        private fun onAddEntry() {
            //TODO show file_browser-dlg
            Toast.makeText(this.itemView.context, "DynplaylistExcludeEditor::add clicked", Toast.LENGTH_SHORT).show()
        }
    }
}

internal class DynplaylistIncludeEditor(val rule: IncludeRule) : NestedExpandableItem<DynplaylistIncludeEditor.ViewHolder>(withBorder = true) {

    override val type: Int = IncludeRule::class.hashCode()
    override val layoutRes: Int = -1

    override var isSelectable: Boolean
        get() = false
        set(_) {}

    init {
        listEntries()
    }

    private fun listEntries() {
        addSubItems(rule.getDirs().map { MediaNodeElement(it.first) })
        addSubItems(rule.getFiles().map { MediaNodeElement(it) })
    }

    override fun createView(ctx: Context, parent: ViewGroup?): View {
        return SimpleAddableRuleContentView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    internal class ViewHolder(view: View) : FastAdapter.ViewHolder<DynplaylistIncludeEditor>(view) {

        override fun bindView(item: DynplaylistIncludeEditor, payloads: List<Any>) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.title.text = "Include"//TODO rules should have names
            view.addBtn.setOnClickListener {
                onAddEntry()
            }
        }

        override fun unbindView(item: DynplaylistIncludeEditor) {
            val view = this.itemView as SimpleAddableRuleContentView
            view.addBtn.setOnContextClickListener(null)
        }

        private fun onAddEntry() {
            //TODO show file_browser-dlg
            Toast.makeText(this.itemView.context, "DynplaylistIncludeEditor::add clicked", Toast.LENGTH_SHORT).show()
        }
    }
}
//endregion

//region util classes

private class SimpleAddableRuleContentView(context: Context) : LinearLayout(context) {

    val title = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
    }
    val addBtn = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_add_24)
        imageTintList = ColorStateList.valueOf(Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
    }

    init {
        this.orientation = HORIZONTAL
        this.addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 5f))
        this.addView(addBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f))
    }
}

private class MediaNodeElement(val path: MediaNode) : AbstractItem<MediaNodeElement.ViewHolder>(), ISubItem<MediaNodeElement.ViewHolder> {

    //TODO extend visible information; add button for remove, ...

    override val type: Int = MediaNode::class.hashCode()
    override val layoutRes: Int = -1
    override var parent: IParentItem<*>? = null

    override fun createView(ctx: Context, parent: ViewGroup?): View {
        return ContentView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<MediaNodeElement>(view) {

        override fun bindView(item: MediaNodeElement, payloads: List<Any>) {
            val view = this.itemView as ContentView
            view.text.text = item.path.path
        }

        override fun unbindView(item: MediaNodeElement) {
            ;
        }
    }

    private class ContentView(context: Context) : LinearLayout(context) {

        val text = TextView(context)

        init {
            this.addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }
}
//endregion
