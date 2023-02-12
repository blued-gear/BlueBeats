package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ExcludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rulelike
import apps.chocolatecakecodes.bluebeats.view.specialitems.NestedExpandableItem
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.items.AbstractItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal typealias ChangedCallback = (Rulelike) -> Unit

internal fun createEditor(item: Rulelike, cb: ChangedCallback): AbstractExpandableItem<*> {
    return when(item) {
        is RuleGroup -> DynplaylistGroupEditor(item, cb)
        is ExcludeRule -> DynplaylistExcludeEditor(item, cb)
        is IncludeRule -> DynplaylistIncludeEditor(item, cb)
        else -> throw IllegalArgumentException("unsupported rule")
    }
}

//region editors
internal class DynplaylistGroupEditor(
    val group: RuleGroup,
    private val changedCallback: ChangedCallback
) : NestedExpandableItem<DynplaylistGroupEditor.ViewHolder>(withBorder = true) {

    override val type: Int = RuleGroup::class.hashCode()
    override val layoutRes: Int = -1

    override var isSelectable: Boolean
        get() = false
        set(_) {}

    init {
        listRules()
    }

    private fun listRules() {
        addSubItems(group.getExcludes().map { createEditor(it, changedCallback) })
        addSubItems(group.getRules().map { createEditor(it, changedCallback) })
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

        private val ruleGenerators = mapOf(
            view.context.getString(R.string.dynpl_type_group) to {
                RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(Rule.Share(1f, true))
            },
            view.context.getString(R.string.dynpl_type_exclude) to {
                RoomDB.DB_INSTANCE.dplExcludeRuleDao().createNew()
            },
            view.context.getString(R.string.dynpl_type_include) to {
                RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(Rule.Share(1f, true))
            }
        )

        private var item: DynplaylistGroupEditor? = null

        override fun bindView(item: DynplaylistGroupEditor, payloads: List<Any>) {
            this.item = item

            val view = this.itemView as SimpleAddableRuleContentView
            view.title.text = "Group"//TODO rules should have names
            view.addBtn.setOnClickListener {
                onAddRule()
            }
        }

        override fun unbindView(item: DynplaylistGroupEditor) {
            this.item = null

            val view = this.itemView as SimpleAddableRuleContentView
            view.addBtn.setOnContextClickListener(null)
        }

        private fun onAddRule() {
            val spinner = Spinner(this.itemView.context).apply {
                this.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, ruleGenerators.keys.toTypedArray())
            }

            AlertDialog.Builder(this.itemView.context)
                .setTitle(R.string.dynpl_edit_select_rule_type)
                .setView(spinner)
                .setNegativeButton(R.string.misc_cancel) { dlg, _ ->
                    dlg.cancel()
                }
                .setPositiveButton(R.string.misc_add) {dlg, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val rule = ruleGenerators[spinner.selectedItem]!!()
                        item!!.group.addRule(rule)

                        withContext(Dispatchers.Main) {
                            dlg.dismiss()

                            item!!.let {
                                it.addSubItem(createEditor(rule, it.changedCallback))
                                it.changedCallback(it.group)
                            }
                        }
                    }
                }
                .show()
        }
    }
}

internal class DynplaylistExcludeEditor(
    val rule: ExcludeRule,
    private val changedCallback: ChangedCallback
) : NestedExpandableItem<DynplaylistExcludeEditor.ViewHolder>(withBorder = true) {

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

internal class DynplaylistIncludeEditor(
    val rule: IncludeRule,
    private val changedCallback: ChangedCallback
) : NestedExpandableItem<DynplaylistIncludeEditor.ViewHolder>(withBorder = true) {

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
