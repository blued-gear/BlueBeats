package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.setPadding
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.view.specialviews.FileBrowserView
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate

@PackagePrivate
internal class MediaNodeSelectPopup(
    ctx: Context,
    private val initialDir: MediaDir,
    private val allowDirs: Boolean,
    private val onResult: (List<MediaNode>) -> Unit
) : LinearLayout(ctx) {

    private val browser = FileBrowserView(ctx)

    init {
        setBackgroundColor(
            MaterialColors.getColor(this,
            android.R.attr.colorBackground, Color.MAGENTA))
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
