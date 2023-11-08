package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import apps.chocolatecakecodes.bluebeats.R
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate

@PackagePrivate
internal class TextSelector(
    tags: List<String>,
    selectedTags: Set<String>,
    ctx: Context,
    onResult: (List<String>?) -> Unit
): FrameLayout(ctx) {

    private lateinit var selects: List<CheckBox>
    private var selectsContainer: ViewGroup

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

        setBackgroundColor(
            MaterialColors.getColor(this,
            android.R.attr.colorBackground, Color.MAGENTA))

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
