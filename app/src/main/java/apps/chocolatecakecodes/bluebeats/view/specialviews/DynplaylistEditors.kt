package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.*
import apps.chocolatecakecodes.bluebeats.util.Debouncer
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.regex.PatternSyntaxException
import kotlin.math.truncate

internal typealias ChangedCallback = (GenericRule) -> Unit

internal fun createEditorRoot(
    root: RuleGroup,
    cb: ChangedCallback,
    ctx: Context
): View {
    return DynplaylistGroupEditor(root, cb, ctx).apply {
        header.shareBtn.visibility = View.GONE// root-group always has 100% share
    }
}

//region editors
private class DynplaylistGroupEditor(
    val group: RuleGroup,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    private val ruleGenerators = mapOf(
        context.getString(R.string.dynpl_type_group) to {
            RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(Rule.Share(1f, true))
        },
        context.getString(R.string.dynpl_type_include) to {
            RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(Rule.Share(1f, true))
        },
        context.getString(R.string.dynpl_type_usertags) to {
            RoomDB.DB_INSTANCE.dplUsertagsRuleDao().createNew(Rule.Share(1f, true))
        },
        context.getString(R.string.dynpl_type_id3tag) to {
            RoomDB.DB_INSTANCE.dplID3TagsRuleDao().create(Rule.Share(1f, true))
        },
        context.getString(R.string.dynpl_regex_title) to {
            RoomDB.DB_INSTANCE.dplRegexRuleDao().createNew(Rule.Share(1f, true))
        }
    )

    private val addBtn = SimpleAddableRuleHeaderView.CommonVisuals.addButton(context)
    private val logicBtn = SimpleAddableRuleHeaderView.CommonVisuals.logicButton(context)

    init {
        addBtn.setOnClickListener {
            onAddEntry()
        }

        logicBtn.apply {
            setOnClickListener {
                group.combineWithAnd = !group.combineWithAnd
                setLogicMode(group.combineWithAnd)
                changedCallback(group)
            }
            setLogicMode(group.combineWithAnd)
        }

        header.apply {
            title.text = "Group"
            setupShareEdit(group.share) {
                group.share = it
                changedCallback(group)
            }
            addVisual(addBtn, true)
            addVisual(logicBtn, false)
        }

        listItems()
    }

    private fun listItems() {
        contentList.removeAllViews()

        val lp = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        group.getRules().map(this::createItem).forEach {
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
                        contentList.addView(createItem(Pair(rule, group.getRuleNegated(rule)!!)))
                        expander.expanded = true
                    }
                }
            }
            .show()
    }

    private fun createItem(ruleItem: Pair<GenericRule, Boolean>): AbstractDynplaylistEditorView {
        return createEditor(ruleItem.first, changedCallback, this.context).apply editorView@{
            SimpleAddableRuleHeaderView.CommonVisuals.negateCheckbox(context).apply {
                setOnCheckedChangeListener { _, checked ->
                    group.setRuleNegated(ruleItem.first, checked)
                    if(checked != ruleItem.second)
                        changedCallback(group)
                    this@editorView.header.shareBtn.visibility = if(checked) View.GONE else View.VISIBLE
                }
                this.isChecked = ruleItem.second
            }.let {
                this.header.addVisual(it, false)
            }

            ImageButton(context).apply {
                setImageResource(R.drawable.ic_baseline_remove_24)
                imageTintList = ColorStateList.valueOf(Color.BLACK)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    group.removeRule(ruleItem.first)
                    changedCallback(group)
                    contentList.removeView(this@editorView)
                }
            }.let {
                this.header.addVisual(it, true)
            }
        }
    }
}

private class DynplaylistIncludeEditor(
    val rule: IncludeRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    private var lastDir: MediaDir = VlcManagers.getMediaDB().getSubject().getMediaTreeRoot()

    init {
        header.title.text = "Include"

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        SimpleAddableRuleHeaderView.CommonVisuals.addButton(context).apply {
            setOnClickListener {
                onAddEntry()
            }
        }.let {
            header.addVisual(it, true)
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
                        rule.setDirDeep(item.first, checked)
                        if(checked != item.second)
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
}

private class DynplaylistUsertagsEditor(
    val rule: UsertagsRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    val logicBtn = SimpleAddableRuleHeaderView.CommonVisuals.logicButton(context)
    val addBtn = SimpleAddableRuleHeaderView.CommonVisuals.addButton(context)

    init {
        header.title.text = "Tags"

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        logicBtn.apply {
            setOnClickListener {
                onToggleLogicMode()
            }
            setLogicMode(rule.combineWithAnd)
        }.let {
            header.addVisual(it, false)
        }

        addBtn.apply {
            setOnClickListener {
                onAddEntry()
            }
        }.let {
            header.addVisual(it, true)
        }

        listItems()
    }

    private fun listItems() {
        contentList.removeAllViews()

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getTags().map { tag ->
            TextElement(tag, context).apply {
                removeBtn.setOnClickListener {
                    onRemoveEntry(tag)
                }
            }
        }.forEach {
            contentList.addView(it, lp)
        }
    }

    private fun onAddEntry() {
        CoroutineScope(Dispatchers.IO).launch {
            RoomDB.DB_INSTANCE.userTagDao().getAllUserTags().let { availableTags ->
                withContext(Dispatchers.Main) {
                    var popup: PopupWindow? = null
                    val popupContent = TextSelector(availableTags, rule.getTags(), context) { selection ->
                        if(selection !== null){
                            Utils.diffChanges(rule.getTags(), selection.toSet()).let { (added, deleted, _) ->
                                deleted.forEach(rule::removeTag)
                                added.forEach(rule::addTag)
                            }

                            changedCallback(rule)
                            listItems()
                            expander.expanded = true
                        }

                        popup!!.dismiss()
                    }

                    popup = PopupWindow(popupContent,
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        true
                    )

                    popup.showAtLocation(this@DynplaylistUsertagsEditor, Gravity.CENTER, 0, 0)
                }
            }
        }
    }

    private fun onRemoveEntry(tag: String) {
        rule.removeTag(tag)
        changedCallback(rule)
        listItems()
    }

    private fun onToggleLogicMode() {
        rule.combineWithAnd = !rule.combineWithAnd
        changedCallback(rule)
        logicBtn.setLogicMode(rule.combineWithAnd)
    }
}

private class DynplaylistID3TagsEditor(
    val rule: ID3TagsRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    val addBtn = SimpleAddableRuleHeaderView.CommonVisuals.addButton(context)
    val typeSelect = Spinner(ctx)
    val typeSelectAdapter = ArrayAdapter<String>(ctx, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
    val valuesContainer: LinearLayout
    val availableValues = ArrayList<String>()

    init {
        header.title.text = "ID3 Tags"

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        addBtn.apply {
            setOnClickListener {
                onAddEntry()
            }
        }.let {
            header.addVisual(it, true)
        }

        typeSelect.adapter = typeSelectAdapter
        typeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                onTypeSelected(typeSelectAdapter.getItem(position)!!)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                throw AssertionError()
            }
        }

        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL

            TextView(ctx).apply {
                setText(R.string.dynpl_edit_tag_type)
            }.let {
                this.addView(it)
            }
            this.addView(typeSelect)
        }.also {
            contentList.addView(it)
        }

        valuesContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }.also {
            contentList.addView(it)
        }

        listItems()
    }

    private fun listItems() {
        valuesContainer.removeAllViews()

        loadTypes()
        loadTypeValues()

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rule.getTagValues().map { tag ->
            TextElement(tag, context).apply {
                removeBtn.setOnClickListener {
                    onRemoveEntry(tag)
                }
            }
        }.forEach {
            valuesContainer.addView(it, lp)
        }
    }

    private fun onAddEntry() {
        synchronized(availableValues) {
            var popup: PopupWindow? = null
            val popupContent = TextSelector(availableValues, rule.getTagValues(), context) { selection ->
                if (selection !== null) {
                    Utils.diffChanges(rule.getTagValues(), selection.toSet())
                        .let { (added, deleted, _) ->
                            deleted.forEach(rule::removeTagValue)
                            added.forEach(rule::addTagValue)
                        }

                    changedCallback(rule)
                    listItems()
                    expander.expanded = true
                }

                popup!!.dismiss()
            }

            popup = PopupWindow(
                popupContent,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                true
            )

            popup.showAtLocation(this, Gravity.CENTER, 0, 0)
        }
    }

    private fun onRemoveEntry(tag: String) {
        rule.removeTagValue(tag)
        changedCallback(rule)
        listItems()
    }

    private fun onTypeSelected(newType: String) {
        if(newType != rule.tagType) {
            rule.tagType = newType
            changedCallback(rule)
            listItems()
        }
    }

    private fun loadTypes() {
        CoroutineScope(Dispatchers.IO).launch {
            RoomDB.DB_INSTANCE.id3TagDao().getAllTagTypes().let { types ->
                withContext(Dispatchers.Main) {
                    typeSelectAdapter.clear()
                    types.filterNot {
                        it == "length"
                    }.let {
                        typeSelectAdapter.addAll(it)
                    }

                    typeSelect.setSelection(typeSelectAdapter.getPosition(rule.tagType))
                }
            }
        }
    }

    private fun loadTypeValues() {
        CoroutineScope(Dispatchers.IO).launch {
            RoomDB.DB_INSTANCE.id3TagDao().getAllTypeValues(rule.tagType).let { values ->
                synchronized(availableValues) {
                    availableValues.clear()
                    availableValues.addAll(values)
                }
            }
        }
    }
}

private class DynplaylistRegexEditor(
    val rule: RegexRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    val typeSelect = Spinner(ctx)
    val typeSelectAdapter = ArrayAdapter<String>(ctx, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
    val invalidRegexIndicator = TextView(ctx)

    val regexTextDebouncer = Debouncer.create<String>(50) {
        onRegexChanged(it)
    }

    init {
        header.title.text = ctx.getString(R.string.dynpl_regex_title)

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        TextView(ctx).apply {
            text = ctx.getText(R.string.dynpl_regex_attr)
        }.let {
            this.contentList.addView(it)
        }

        typeSelect.adapter = typeSelectAdapter
        typeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                onTypeSelected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                throw AssertionError()
            }
        }
        this.contentList.addView(typeSelect)

        loadTypeValues()
        typeSelect.setSelection(rule.attribute.ordinal)

        TextView(ctx).apply {
            text = ctx.getText(R.string.dynpl_regex_regex)
        }.let {
            this.contentList.addView(it)
        }

        EditText(ctx).apply {
            setSingleLine()
            text.append(rule.regex)

            addTextChangedListener {
                if (it != null)
                    regexTextDebouncer.debounce(it.toString())
            }
        }.let {
            this.contentList.addView(it)
        }

        invalidRegexIndicator.apply {
            setTextColor(Color.RED)
            setTextSize(TypedValue.COMPLEX_UNIT_FRACTION_PARENT, 12f)
            setPadding(32, 0, 0, 0)
        }.let {
            this.contentList.addView(it)
        }
    }

    fun onTypeSelected(idx: Int) {
        rule.attribute = RegexRule.Attribute.values()[idx]
        changedCallback(rule)
    }

    fun onRegexChanged(regex: String) {
        try {
            Regex(regex)
        } catch(ignored: PatternSyntaxException) {
            CoroutineScope(Dispatchers.Main).launch {
                invalidRegexIndicator.text = context.getText(R.string.dynpl_regex_regex_invalid)
            }
            return
        }

        rule.regex = regex
        CoroutineScope(Dispatchers.Main).launch {
            invalidRegexIndicator.text = ""
            changedCallback(rule)
        }
    }

    private fun loadTypeValues() {
        RegexRule.Attribute.values().map {
            it.name
        }.map { // capitalize
            it.lowercase().replaceFirstChar {
                if (it.isLowerCase())
                    it.titlecase(Locale.getDefault())
                else
                    it.toString()
            }
        }.let {
            typeSelectAdapter.addAll(it)
        }
    }
}
//endregion

//region private utils / vals
private const val SUBITEM_INSET = 40

private fun createEditor(
    item: GenericRule,
    cb: ChangedCallback,
    ctx: Context
): AbstractDynplaylistEditorView {
    return when(item) {
        is RuleGroup -> DynplaylistGroupEditor(item, cb, ctx)
        is IncludeRule -> DynplaylistIncludeEditor(item, cb, ctx)
        is UsertagsRule -> DynplaylistUsertagsEditor(item, cb, ctx)
        is ID3TagsRule -> DynplaylistID3TagsEditor(item, cb, ctx)
        is RegexRule -> DynplaylistRegexEditor(item, cb, ctx)
        else -> throw IllegalArgumentException("unsupported rule")
    }
}


private abstract class AbstractDynplaylistEditorView(ctx: Context) : FrameLayout(ctx) {

    protected val expander: ExpandableCard
    protected val contentList: LinearLayout
    val header: SimpleAddableRuleHeaderView

    init {
        contentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        header = SimpleAddableRuleHeaderView(context)

        expander = ExpandableCard(context, header, contentList, true, SUBITEM_INSET).also {
            this.addView(it)
        }
    }
}

private class SimpleAddableRuleHeaderView(ctx: Context) : LinearLayout(ctx) {

    object CommonVisuals {

        fun addButton(ctx: Context) = AppCompatImageButton(ctx).apply {
            setImageResource(R.drawable.ic_baseline_add_24)
            imageTintList = ColorStateList.valueOf(Color.BLACK)
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun negateCheckbox(ctx: Context) = CheckBox(ctx).apply {
            text = "negate"
        }

        fun logicButton(ctx: Context) = LogicButton(ctx)

        class LogicButton(ctx: Context) : AppCompatImageButton(ctx) {
            fun setLogicMode(and: Boolean) {
                if(and)
                    this.setImageResource(R.drawable.ic_intersect)
                else
                    this.setImageResource(R.drawable.ic_union)
            }
        }
    }

    val title = TextView(context)
    val shareBtn = Button(context)

    private lateinit var ruleShare: Rule.Share

    init {
        this.orientation = HORIZONTAL

        addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 5f))
    }

    /**
     * @param atEnd if true item will be placed as the last view; else it will be placed after the share-btn
     */
    fun addVisual(item: View, atEnd: Boolean) {
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f)
        if(atEnd)
            this.addView(item, lp)
        else
            this.addView(item, 2, lp)
    }

    fun setupShareEdit(initialShare: Rule.Share, onEdited: (Rule.Share) -> Unit) {
        ruleShare = initialShare
        setShareBtnText(initialShare)
        shareBtn.setOnClickListener(onEditShareHandler(onEdited))

        if(shareBtn.parent === null)
            addVisual(shareBtn, true)
    }

    @SuppressLint("SetTextI18n")
    private fun setShareBtnText(share: Rule.Share) {
        if(share.modeRelative()) {
            val sharePercentage = DecimalFormat("#.#").apply {
                roundingMode = RoundingMode.HALF_UP
                multiplier = 100
            }.format(share.value)
            shareBtn.text = "S: ${sharePercentage}%"
        } else if(share.modeAbsolute()) {
                shareBtn.text = "S: ${share.value.toInt()}"
        } else if(share.modeEven()) {
            shareBtn.text = "evn"
        } else if (share.modeUnlimited()) {
            shareBtn.text = "inf"
        }
    }

    private fun onEditShareHandler(onEditedHandler: (Rule.Share) -> Unit): OnClickListener {
        return OnClickListener{
            var popup: PopupWindow? = null
            val popupContent = ShareEditor(ruleShare, context) { newVal ->
                newVal?.let {
                    if(it != ruleShare) {
                        onEditedHandler(it)
                        ruleShare = it
                        setShareBtnText(it)
                    }
                }

                popup!!.dismiss()
            }

            popup = PopupWindow(
                popupContent,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                true
            )

            popup.showAtLocation(this, Gravity.CENTER, 0, 0)
        }
    }
}

private class ShareEditor(
    initialShare: Rule.Share,
    ctx: Context,
    private val onResult: (Rule.Share?) -> Unit
) : FrameLayout(ctx) {

    private enum class ShareMode { RELATIVE, EVEN, ABSOLUTE, UNLIMITED }

    private val relativeRb: RadioButton
    private val evenRb: RadioButton
    private val absoluteRb: RadioButton
    private val unlimitedRb: RadioButton
    private val valueInp: EditText
    private val okBtn: Button
    private val cancelBtn: Button

    init {
        setBackgroundColor(Color.WHITE)

        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            relativeRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_relative)
                isChecked = initialShare.isRelative && initialShare.value >= 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.RELATIVE)
                }
            }.also {
                addView(it)
            }
            evenRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_even)
                isChecked = initialShare.isRelative && initialShare.value < 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.EVEN)
                }
            }.also {
                addView(it)
            }
            absoluteRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_absolute)
                isChecked = !initialShare.isRelative && initialShare.value >= 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.ABSOLUTE)
                }
            }.also {
                addView(it)
            }
            unlimitedRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_unlimited)
                isChecked = !initialShare.isRelative && initialShare.value < 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.UNLIMITED)
                }
            }.also {
                addView(it)
            }

            valueInp = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                setSingleLine()
                if(initialShare.isRelative){
                    inputType = inputType or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
                    val shareDecimal = DecimalFormatSymbols().apply {
                        decimalSeparator = '.'
                    }.let { formatSymbols ->
                        DecimalFormat("#.####", formatSymbols).apply {
                            roundingMode = RoundingMode.HALF_UP
                        }.format(initialShare.value)
                    }
                    text.append(shareDecimal)
                }else{
                    text.append(initialShare.value.toInt().toString())
                }

            }.also {
                addView(it)
            }

            okBtn = Button(context).apply {
                text = context.getString(R.string.misc_ok)
                setOnClickListener {
                    onOk()
                }
            }
            cancelBtn = Button(context).apply {
                text = context.getString(R.string.misc_cancel)
                setOnClickListener {
                    onCancel()
                }
            }

            // bottom bar
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                this.addView(cancelBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                this.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 5f))
                this.addView(okBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }.let {
                this.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }.let {
            this.addView(it)
        }
    }

    private fun onOk() {
        var shareVal = valueInp.text.toString().toFloat()
        if(unlimitedRb.isChecked) {
            onResult(Rule.Share(-1f, false))
        } else if(evenRb.isChecked) {
            onResult(Rule.Share(-1f, true))
        } else {
            val relative = relativeRb.isChecked
            if(relative)
                shareVal = shareVal.coerceAtMost(1f)
            onResult(Rule.Share(
                if(relative) shareVal else truncate(shareVal),
                relative
            ))
        }
    }

    private fun onCancel() {
        onResult(null)
    }

    private fun onShareModeChanged(mode: ShareMode) {
        when(mode) {
            ShareMode.RELATIVE -> {
                valueInp.isEnabled = true
                valueInp.inputType = valueInp.inputType or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL

                evenRb.isChecked = false
                absoluteRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.EVEN -> {
                valueInp.isEnabled = false

                relativeRb.isChecked = false
                absoluteRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.ABSOLUTE -> {
                valueInp.isEnabled = true
                valueInp.inputType = valueInp.inputType and EditorInfo.TYPE_NUMBER_FLAG_DECIMAL.inv()
                valueInp.text.apply {
                    val currentVal = this.toString().toFloat()
                    clear()
                    append(currentVal.toInt().toString())
                }

                relativeRb.isChecked = false
                evenRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.UNLIMITED -> {
                valueInp.isEnabled = false

                relativeRb.isChecked = false
                evenRb.isChecked = false
                absoluteRb.isChecked = false
            }
        }
    }
}

private class MediaNodeView(val path: MediaNode, context: Context, showDeepCB: Boolean = true) : LinearLayout(context) {

    //TODO extend visible information

    val text = TextView(context).apply {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MIDDLE
        text = path.path
        gravity = Gravity.START
    }
    val removeBtn = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_remove_24)
        imageTintList = ColorStateList.valueOf(Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
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
                text = context.getString(R.string.misc_cancel)
                setOnClickListener {
                    onResult(emptyList())
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
                text = context.getString(R.string.misc_ok)
                setOnClickListener {
                    // if nothing is selected use the current dir
                    val selected = if(browser.inSelection) browser.selectedItems else listOf(browser.currentDir!!)
                    onResult(selected)
                }
            }.let {
                this.addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
        }.let {
            this.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
}

private class TextElement(val tag: String, ctx: Context): LinearLayout(ctx) {

    val removeBtn = ImageButton(context).apply {
        gravity = Gravity.END
        setImageResource(R.drawable.ic_baseline_remove_24)
        imageTintList = ColorStateList.valueOf(Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
    }

    val text = TextView(context).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        text = this@TextElement.tag
    }

    init {
        this.orientation = HORIZONTAL

        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 10f))
        addView(removeBtn, LayoutParams(100, LayoutParams.WRAP_CONTENT))
    }
}

private class TextSelector(
    tags: List<String>,
    selectedTags: Set<String>,
    ctx: Context,
    onResult: (List<String>?) -> Unit
): FrameLayout(ctx) {

    private lateinit var selects: List<CheckBox>
    private lateinit var selectsContainer: ViewGroup

    private val okBtn = Button(context).apply {
        text = context.getString(R.string.misc_ok)
        setOnClickListener {
            selects.filter {
                it.isChecked
            }.map {
                it.text.toString()
            }.let {
                onResult(it)
            }
        }
    }

    private val cancelBtn = Button(context).apply {
        text = context.getString(R.string.misc_cancel)
        setOnClickListener {
            onResult(null)
        }
    }

    init {
        selects = tags.map {
            CheckBox(context).apply {
                text = it
                isChecked = selectedTags.contains(it)
            }
        }

        setBackgroundColor(Color.WHITE)

        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setupSearch().let {
                this.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }

            // selection-list
            ScrollView(context).apply {
                setPadding(8)

                selectsContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL

                    selects.forEach {
                        addView(it)
                    }
                }.also {
                    addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                }
            }.let {
                this.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 10f))
            }

            // bottom bar
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                this.addView(cancelBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                this.addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 5f))
                this.addView(okBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }.let {
                this.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }.let {
            this.addView(it)
        }
    }

    private fun setupSearch(): View {
        return EditText(context).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.baseline_search_24, 0)
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.BLUE))

            addTextChangedListener {
                if (it != null)
                    filter(it)
            }
        }
    }

    private fun filter(text: CharSequence) {
        selectsContainer.removeAllViews()

        selects.filter {
            it.text.contains(text, true)
        }.forEach {
            selectsContainer.addView(it)
        }
    }
}
//endregion
