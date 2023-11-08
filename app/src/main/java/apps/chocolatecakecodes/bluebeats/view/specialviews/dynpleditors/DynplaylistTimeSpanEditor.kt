package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.widget.addTextChangedListener
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.TimeSpanRule
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.view.specialviews.TimeEdit
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@PackagePrivate
internal class DynplaylistTimeSpanEditor(
    val rule: TimeSpanRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    private val path: TextView
    private val startEdit: TimeEdit
    private val endEdit: TimeEdit
    private val desc: EditText
    private val selectChapterBtn: ImageButton
    private var lastDir: MediaDir = VlcManagers.getMediaDB().getSubject().getMediaTreeRoot()

    init {
        header.title.text = context.getText(R.string.dynpl_type_timespan)

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        AppCompatImageButton(ctx).apply {
            setImageResource(R.drawable.ic_baseline_insert_drive_file_24)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this,
                android.R.attr.colorForeground, Color.BLACK))

            setOnClickListener {
                onSelectFile()
            }
        }.let {
            header.addVisual(it, true)
        }

        selectChapterBtn = AppCompatImageButton(ctx).apply {
            setImageResource(R.drawable.baseline_queue_music_24)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this,
                android.R.attr.colorForeground, Color.BLACK))

            visibility = View.GONE

            setOnClickListener {
                onSelectChapter()
            }
        }.also {
            header.addVisual(it, false)
        }

        TextView(context).apply {
            text = context.getText(R.string.misc_path)
        }.let {
            contentList.addView(it)
        }
        path = TextView(context).apply {
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }.also {
            contentList.addView(it)
        }

        TextView(context).apply {
            text = context.getText(R.string.misc_description)
        }.let {
            contentList.addView(it)
        }
        desc = EditText(ctx).apply {
            setSingleLine()
            text.append(rule.description)

            addTextChangedListener(afterTextChanged = {
                if (it != null)
                    onDescChanged(it.toString())
            })
        }.also {
            this.contentList.addView(it)
        }

        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL

            TextView(context).apply {
                text = context.getText(R.string.dynpl_timespan_start)
                gravity = Gravity.CENTER_VERTICAL
            }.let {
                addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }

            startEdit = TimeEdit(context).apply {
                setTime(rule.startMs)
                addChangedListener {
                    if(it != null)
                        onStartChanged(it)
                }
            }.also {
                addView(it)
            }
        }.let {
            contentList.addView(it)
        }
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL

            TextView(context).apply {
                text = context.getText(R.string.dynpl_timespan_end)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 0, 0, 0)
            }.let {
                addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }

            endEdit = TimeEdit(context).apply {
                setTime(rule.endMs)
                addChangedListener {
                    if(it != null)
                        onEndChanged(it)
                }
            }.also {
                addView(it)
            }
        }.let {
            contentList.addView(it)
        }

        updateFileDetails(rule.file)
    }

    private fun onSelectFile() {
        var popup: PopupWindow? = null
        val popupContent = MediaNodeSelectPopup(context, lastDir, false) {
            popup!!.dismiss()

            if(it.isNotEmpty()) {
                val file = it.first() as MediaFile

                lastDir = file.parent

                onFileChanged(file)
                expander.expanded = true
            }
        }
        popup = PopupWindow(popupContent,
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            true
        )

        popup.showAtLocation(this, Gravity.CENTER, 0, 0)
    }

    private fun onSelectChapter() {
        val dlgBuilder = AlertDialog.Builder(context)

        // generate chapter items
        val chapters = rule.file.chapters!!
        val itemTexts = chapters.map {
            val start = Utils.formatTime(it.start)
            val end = Utils.formatTime(it.end)
            return@map "${it.name} ($start - $end)"
        }.toTypedArray()

        dlgBuilder.setItems(itemTexts){ _, itemIdx ->
            val chapter = chapters[itemIdx]

            onStartChanged(chapter.start)
            onEndChanged(chapter.end)
            onDescChanged("Chapter: ${chapter.name}")
        }

        dlgBuilder.create().show()
    }

    private fun onFileChanged(file: MediaFile) {
        rule.file = file
        changedCallback(rule)

        updateFileDetails(file)
    }

    private fun updateFileDetails(file: MediaFile) {
        CoroutineScope(Dispatchers.IO).launch {
            val pathStr = if(file === MediaNode.INVALID_FILE)
                ""
            else
                file.path
            val hasChapters = !file.chapters.isNullOrEmpty()
            val length = file.mediaTags.length

            withContext(Dispatchers.Main) {
                path.text = pathStr

                if(hasChapters) {
                    selectChapterBtn.visibility = VISIBLE
                } else {
                    selectChapterBtn.visibility = GONE
                }

                if(length > 0) {
                    if(rule.startMs > length)
                        onStartChanged(0)
                    if(rule.endMs > length)
                        onEndChanged(length)
                }
            }
        }
    }

    private fun onStartChanged(ms: Long) {
        if(ms == rule.startMs)
            return

        rule.startMs = ms
        changedCallback(rule)

        startEdit.setTime(ms)
    }

    private fun onEndChanged(ms: Long) {
        if(ms == rule.endMs)
            return

        rule.endMs = ms
        changedCallback(rule)

        endEdit.setTime(ms)
    }

    private fun onDescChanged(text: String) {
        if(text == rule.description)
            return

        rule.description = text
        changedCallback(rule)

        desc.text.replace(0, desc.text.length, text)
    }
}
