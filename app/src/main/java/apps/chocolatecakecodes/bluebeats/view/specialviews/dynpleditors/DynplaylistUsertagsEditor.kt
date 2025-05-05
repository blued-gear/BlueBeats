package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.PopupWindow
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.UsertagsRule
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.Utils
import io.github.esentsov.PackagePrivate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@PackagePrivate
internal class DynplaylistUsertagsEditor(
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
