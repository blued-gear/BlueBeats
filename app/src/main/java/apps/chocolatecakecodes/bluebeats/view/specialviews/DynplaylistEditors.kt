package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
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
import kotlinx.coroutines.*
import net.cachapa.expandablelayout.ExpandableLayout

internal typealias ChangedCallback = (Rulelike) -> Unit

internal fun createEditor(item: Rulelike, cb: ChangedCallback, ctx: Context): View {
    return when(item) {
        is RuleGroup -> DynplaylistGroupEditor(item, cb, ctx)
        is ExcludeRule -> DynplaylistExcludeEditor(item, cb, ctx)
        is IncludeRule -> DynplaylistIncludeEditor(item, cb, ctx)
        else -> throw IllegalArgumentException("unsupported rule")
    }
}

//region editors
internal class DynplaylistGroupEditor(
    val group: RuleGroup,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : FrameLayout(ctx) {

    private val ruleGenerators = mapOf(
        context.getString(R.string.dynpl_type_group) to {
            RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(Rule.Share(1f, true))
        },
        context.getString(R.string.dynpl_type_exclude) to {
            RoomDB.DB_INSTANCE.dplExcludeRuleDao().createNew()
        },
        context.getString(R.string.dynpl_type_include) to {
            RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(Rule.Share(1f, true))
        }
    )

    private val header: SimpleAddableRuleHeaderView
    private val contentList: LinearLayout

    init {
        header = SimpleAddableRuleHeaderView(context).apply {
            title.text = "Group"//TODO rules should have names
            addBtn.setOnClickListener {
                onAddEntry()
            }
        }
        contentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        ExpandableCard(context, header, contentList, true, SUBITEM_INSET).let {
            this.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        listItems()
    }

    private fun listItems() {
        val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        group.getExcludesAndRules().map {
            createEditor(it, changedCallback, this.context)
        }.forEach {
            contentList.addView(it, lp)
        }
    }

    private fun onAddEntry() {
        val spinner = Spinner(this.context).apply {
            this.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, ruleGenerators.keys.toTypedArray())
        }

        AlertDialog.Builder(this.context)
            .setTitle(R.string.dynpl_edit_select_rule_type)
            .setView(spinner)
            .setNegativeButton(R.string.misc_cancel) { dlg, _ ->
                dlg.cancel()
            }
            .setPositiveButton(R.string.misc_add) {dlg, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val rule = ruleGenerators[spinner.selectedItem]!!()
                    group.addRule(rule)

                    withContext(Dispatchers.Main) {
                        dlg.dismiss()

                        changedCallback(this@DynplaylistGroupEditor.group)
                        contentList.addView(createEditor(rule, changedCallback, this@DynplaylistGroupEditor.context))
                    }
                }
            }
            .show()
    }
}

internal class DynplaylistExcludeEditor(
    val rule: ExcludeRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : FrameLayout(ctx) {

    private val header: SimpleAddableRuleHeaderView
    private val contentList: LinearLayout

    init {
        header = SimpleAddableRuleHeaderView(context).apply {
            title.text = "Exclude"//TODO rules should have names
            addBtn.setOnClickListener {
                onAddEntry()
            }
        }
        contentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        ExpandableCard(context, header, contentList, true, SUBITEM_INSET).let {
            this.addView(it)
        }

        listItems()
    }

    private fun listItems() {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getDirs().map {
            MediaNodeView(it.first, this.context)
        }.forEach {
            contentList.addView(it, lp)
        }
        rule.getFiles().map {
            MediaNodeView(it, this.context)
        }.forEach {
            contentList.addView(it, lp)
        }
    }

    private fun onAddEntry() {

    }
}

internal class DynplaylistIncludeEditor(
    val rule: IncludeRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : FrameLayout(ctx) {

    private val header: SimpleAddableRuleHeaderView
    private val contentList: LinearLayout

    init {
        header = SimpleAddableRuleHeaderView(context).apply {
            title.text = "Include"//TODO rules should have names
            addBtn.setOnClickListener {
                onAddEntry()
            }
        }
        contentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        ExpandableCard(context, header, contentList, true, SUBITEM_INSET).let {
            this.addView(it)
        }

        listItems()
    }

    private fun listItems() {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getDirs().map {
            MediaNodeView(it.first, this.context)
        }.forEach {
            contentList.addView(it, lp)
        }
        rule.getFiles().map {
            MediaNodeView(it, this.context)
        }.forEach {
            contentList.addView(it, lp)
        }
    }

    private fun onAddEntry() {
        //TODO show file_browser-dlg
        Toast.makeText(this.context, "DynplaylistIncludeEditor::add clicked", Toast.LENGTH_SHORT).show()
    }
}
//endregion

//region private utils / vals
private const val SUBITEM_INSET = 40

private class SimpleAddableRuleHeaderView(ctx: Context) : LinearLayout(ctx) {

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

        addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 5f))
        addView(addBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f))
    }
}

private class MediaNodeView(val path: MediaNode, context: Context) : LinearLayout(context) {

    //TODO extend visible information; add button for remove; ...

    private val text = TextView(context).apply {
        ellipsize = TextUtils.TruncateAt.MIDDLE
        text = path.path
    }

    init {
        orientation = HORIZONTAL

        addView(text)
    }
}
//endregion
