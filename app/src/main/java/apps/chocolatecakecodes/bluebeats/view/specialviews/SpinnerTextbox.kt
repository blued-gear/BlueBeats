package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.OnceSettable

internal class SpinnerTextbox : FrameLayout {

    var text: String
        get() = textbox.text.toString()
        set(value) = textbox.setText(value, TextView.BufferType.EDITABLE)

    private var textbox: AutoCompleteTextView by OnceSettable()
    private var itemsAdapter: ArrayAdapter<String> by OnceSettable()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        setup(context)
    }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes){
        setup(context)
    }

    private fun setup(ctx: Context) {
        val layout = LinearLayoutCompat(ctx).apply {
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        itemsAdapter = ArrayAdapter(ctx,
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            mutableListOf()
        )

        textbox = AutoCompleteTextView(ctx).apply {
            this.inputType = EditorInfo.TYPE_CLASS_TEXT and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE.inv()
            setAdapter(itemsAdapter)
        }
        layout.addView(textbox, LinearLayoutCompat.LayoutParams(
            0,
            LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
            0.9f
        ))

        val btn = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_arrow_corner_bottomright)
            imageTintList = ColorStateList.valueOf(Color.GRAY)
            setBackgroundColor(Color.WHITE)

            setOnClickListener {
                textbox.showDropDown()
            }

            setPadding(-64, 0, 0, 0)
        }
        layout.addView(btn, LinearLayoutCompat.LayoutParams(
            64,
            64,
            0.1f
        ))

        this.addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setItems(items: List<String>) {
        itemsAdapter.clear()
        itemsAdapter.addAll(items)
    }

    fun getItems(): List<String> {
        val ret = ArrayList<String>(itemsAdapter.count)
        for(i in 0 until itemsAdapter.count)
            ret.add(itemsAdapter.getItem(i)!!)
        return ret
    }
}