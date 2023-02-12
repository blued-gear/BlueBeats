package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ExcludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rulelike
import kotlinx.coroutines.*

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

    private val expander: ExpandableCard
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

        expander = ExpandableCard(context, header, contentList, true, SUBITEM_INSET).also {
            this.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        listItems()
    }

    private fun listItems() {
        contentList.removeAllViews()

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

                        changedCallback(group)
                        contentList.addView(createEditor(rule, changedCallback, this@DynplaylistGroupEditor.context))
                        expander.expanded = true
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
    private var lastDir: MediaDir = VlcManagers.getMediaDB().getSubject().getMediaTreeRoot()

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
        contentList.removeAllViews()

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getDirs()
            .sortedBy {
                it.first.path
            }.map { item ->
                MediaNodeView(item.first, this.context).apply {
                    removeBtn.setOnClickListener {
                        rule.removeDir(item.first)
                        contentList.removeView(this)
                        changedCallback(rule)
                    }
                    deepCB.isChecked = item.second
                    deepCB.setOnCheckedChangeListener { _, checked ->
                        rule.setDirDeep(item.first, checked)
                        changedCallback(rule)
                    }
                }
            }.forEach {
                contentList.addView(it, lp)
            }
        rule.getFiles()
            .sortedBy {
                it.path
            }.map { item ->
                MediaNodeView(item, this.context).apply {
                    removeBtn.setOnClickListener {
                        rule.removeFile(item)
                        contentList.removeView(this)
                        changedCallback(rule)
                    }
                }
            }.forEach {
                contentList.addView(it, lp)
            }
    }

    private fun onAddEntry() {
        var popup: PopupWindow? = null
        val popupContent = MediaNodeSelectPopup(context, lastDir, true) {
            popup!!.dismiss()

            if(it.isNotEmpty()) {
                // set lastDir to parent of selection
                it[0].let {
                    lastDir = when (it) {
                        is MediaFile -> it.parent
                        is MediaDir -> it.parent ?: it
                        else -> throw AssertionError()
                    }
                }

                it.filterIsInstance<MediaDir>().forEach{ rule.addDir(it, false) }
                it.filterIsInstance<MediaFile>().forEach { rule.addFile(it) }

                listItems()

                changedCallback(rule)
            }
        }
        popup = PopupWindow(popupContent,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            true
        )

        popup.showAtLocation(this, Gravity.CENTER, 0, 0)
    }
}

internal class DynplaylistIncludeEditor(
    val rule: IncludeRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : FrameLayout(ctx) {

    private val header: SimpleAddableRuleHeaderView
    private val contentList: LinearLayout
    private val expander: ExpandableCard
    private var lastDir: MediaDir = VlcManagers.getMediaDB().getSubject().getMediaTreeRoot()

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

        expander = ExpandableCard(context, header, contentList, true, SUBITEM_INSET).also {
            this.addView(it)
        }

        listItems()
    }

    private fun listItems() {
        contentList.removeAllViews()

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getDirs()
            .sortedBy {
                it.first.path
            }.map { item ->
                MediaNodeView(item.first, this.context).apply {
                    removeBtn.setOnClickListener {
                        onRemoveDir(item.first)
                    }
                    deepCB.isChecked = item.second
                    deepCB.setOnCheckedChangeListener { _, checked ->
                        onChangeDirDeep(item.first, checked)
                    }
                }
            }.forEach {
                contentList.addView(it, lp)
            }
        rule.getFiles()
            .sortedBy {
                it.path
            }.map { item ->
                MediaNodeView(item, this.context).apply {
                    removeBtn.setOnClickListener {
                        onRemoveFile(item)
                    }
                }
            }.forEach {
                contentList.addView(it, lp)
            }
    }

    private fun onAddEntry() {
        var popup: PopupWindow? = null
        val popupContent = MediaNodeSelectPopup(context, lastDir, true) {
            popup!!.dismiss()

            if(it.isNotEmpty()) {
                // set lastDir to parent of selection
                it[0].let {
                    lastDir = when (it) {
                        is MediaFile -> it.parent
                        is MediaDir -> it.parent ?: it
                        else -> throw AssertionError()
                    }
                }

                it.filterIsInstance<MediaDir>().forEach{ rule.addDir(it, false) }
                it.filterIsInstance<MediaFile>().forEach { rule.addFile(it) }

                listItems()
                expander.expanded = true

                changedCallback(rule)
            }
        }
        popup = PopupWindow(popupContent,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            true
        )

        popup.showAtLocation(this, Gravity.CENTER, 0, 0)
    }

    private fun onRemoveDir(entry: MediaDir) {
        rule.removeDir(entry)
        contentList.removeView(this)
        changedCallback(rule)
    }

    private fun onRemoveFile(entry: MediaFile) {
        rule.removeFile(entry)
        contentList.removeView(this)
        changedCallback(rule)
    }

    private fun onChangeDirDeep(entry: MediaDir, newVal: Boolean) {
        rule.setDirDeep(entry, newVal)
        changedCallback(rule)
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

private class MediaNodeView(val path: MediaNode, context: Context, showDeepCB: Boolean = true) : LinearLayout(context) {

    //TODO extend visible information; add button for remove; checkbox for deep (opt.); ...

    val text = TextView(context).apply {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MIDDLE
        text = path.path
        gravity = Gravity.START
    }
    val removeBtn = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_remove_24)
        gravity = Gravity.END
    }
    val deepCB = CheckBox(context).apply {
        text = context.getString(R.string.dynpl_edit_deep_dir)
        gravity = Gravity.END
    }

    init {
        orientation = HORIZONTAL

        addView(text, LayoutParams(0, LayoutParams.MATCH_PARENT, 10f))
        if(showDeepCB && path is MediaDir)
            addView(deepCB, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(removeBtn, LayoutParams(100, LayoutParams.WRAP_CONTENT))
    }
}

private class MediaNodeSelectPopup(
    ctx: Context,
    private val initialDir: MediaDir,
    private val allowDirs: Boolean,
    private val onResult: (List<MediaNode>) -> Unit
) : LinearLayout(ctx) {

    private val browser = FileBrowserView(ctx)

    init {
        this.setBackgroundColor(Color.WHITE)
        this.setPadding(24)

        setupContent()

        browser.apply {
            setSelectable(true, allowDirs, true)
            startSelectionWithClick = true
            currentDir = initialDir
        }
    }

    private fun setupContent() {
        this.orientation = VERTICAL

        this.addView(browser, LayoutParams(LayoutParams.MATCH_PARENT, 0, 10f))

        // bottom bar
        LinearLayout(context).apply {
            orientation = HORIZONTAL

            Button(context).apply {
                text = context.getString(R.string.misc_ok)
                setOnClickListener {
                    // if nothing is selected use the current dir
                    val selected = if(browser.inSelection) browser.selectedItems else listOf(browser.currentDir!!)
                    onResult(selected)
                }
            }.let {
                this.addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }

            this.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 5f))

            Button(context).apply {
                text = "/\\"
                setOnClickListener {
                    browser.goDirUp()
                }
            }.let {
                this.addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }

            this.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 5f))

            Button(context).apply {
                text = context.getString(R.string.misc_cancel)
                setOnClickListener {
                    onResult(emptyList())
                }
            }.let {
                this.addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
        }.let {
            this.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
}
//endregion
