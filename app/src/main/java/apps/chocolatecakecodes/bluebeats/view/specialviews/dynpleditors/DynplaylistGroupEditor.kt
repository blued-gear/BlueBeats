package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@PackagePrivate
internal class DynplaylistGroupEditor(
    val group: RuleGroup,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    private val ruleGenerators = mapOf(
        context.getString(R.string.dynpl_type_group) to {
            RoomDB.DB_INSTANCE.dplRuleGroupDao().createNew(Share(-1f, true)).copy()
        },
        context.getString(R.string.dynpl_type_include) to {
            RoomDB.DB_INSTANCE.dplIncludeRuleDao().createNew(Share(-1f, true)).copy()
        },
        context.getString(R.string.dynpl_type_usertags) to {
            RoomDB.DB_INSTANCE.dplUsertagsRuleDao().createNew(Share(-1f, true)).copy()
        },
        context.getString(R.string.dynpl_type_id3tag) to {
            RoomDB.DB_INSTANCE.dplID3TagsRuleDao().create(Share(-1f, true)).copy()
        },
        context.getString(R.string.dynpl_regex_title) to {
            RoomDB.DB_INSTANCE.dplRegexRuleDao().createNew(Share(-1f, true)).copy()
        },
        context.getString(R.string.dynpl_type_timespan) to {
            RoomDB.DB_INSTANCE.dplTimeSpanRuleDao().createNew(Share(-1f, true)).copy()
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

            header.name.apply {
                this.editableText.append(group.name)
                this.doAfterTextChanged { text ->
                    group.name = text.toString()
                    changedCallback(group)
                }
            }

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
            .setPositiveButton(R.string.misc_add) { dlg, _ ->
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
        return DynPlaylistEditors.createEditor(ruleItem.first, changedCallback, this.context).apply editorView@{
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
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(this,
                    android.R.attr.colorForeground, Color.BLACK))
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
