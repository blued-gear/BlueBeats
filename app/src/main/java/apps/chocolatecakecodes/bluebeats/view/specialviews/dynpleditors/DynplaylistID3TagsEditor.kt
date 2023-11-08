package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ID3TagsRule
import apps.chocolatecakecodes.bluebeats.util.Utils
import io.github.esentsov.PackagePrivate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@PackagePrivate
internal class DynplaylistID3TagsEditor(
    val rule: ID3TagsRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    val addBtn = SimpleAddableRuleHeaderView.CommonVisuals.addButton(context)
    val typeSelect = Spinner(ctx)
    val typeSelectAdapter = ArrayAdapter<String>(ctx, R.layout.support_simple_spinner_dropdown_item)
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
                setText(apps.chocolatecakecodes.bluebeats.R.string.dynpl_edit_tag_type)
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
