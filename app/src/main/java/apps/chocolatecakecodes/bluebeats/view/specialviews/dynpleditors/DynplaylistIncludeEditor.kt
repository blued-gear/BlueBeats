package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.children
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import io.github.esentsov.PackagePrivate

@PackagePrivate
internal class DynplaylistIncludeEditor(
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
        contentList.children.first {
            it is MediaNodeView && it.path === entry
        }.let {
            contentList.removeView(it)
        }
        changedCallback(rule)
    }

    private fun onRemoveFile(entry: MediaFile) {
        rule.removeFile(entry)
        contentList.children.first {
            it is MediaNodeView && it.path === entry
        }.let {
            contentList.removeView(it)
        }
        changedCallback(rule)
    }
}
