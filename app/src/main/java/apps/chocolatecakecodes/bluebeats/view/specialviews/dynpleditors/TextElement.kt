package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate

@PackagePrivate
internal class TextElement(val tag: String, ctx: Context): LinearLayout(ctx) {

    val removeBtn = ImageButton(context).apply {
        gravity = Gravity.END
        setImageResource(R.drawable.ic_baseline_remove_24)
        imageTintList = ColorStateList.valueOf(
            MaterialColors.getColor(this,
            android.R.attr.colorForeground, Color.BLACK))
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
