package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate

@PackagePrivate
internal class MediaNodeView(val path: MediaNode, context: Context, showDeepCB: Boolean = true) : LinearLayout(context) {

    //TODO extend visible information

    val text = TextView(context).apply {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MIDDLE
        text = path.path
        gravity = Gravity.START
    }
    val removeBtn = ImageButton(context).apply {
        setImageResource(R.drawable.ic_baseline_remove_24)
        imageTintList = ColorStateList.valueOf(
            MaterialColors.getColor(this,
            android.R.attr.colorForeground, Color.BLACK))
        setBackgroundColor(Color.TRANSPARENT)
        gravity = Gravity.END
    }
    val deepCB = CheckBox(context).apply {
        text = context.getString(R.string.dynpl_edit_deep_dir)
        gravity = Gravity.END
    }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(
            MaterialColors.getColor(this,
            android.R.attr.colorBackground, Color.MAGENTA))

        addView(text, LayoutParams(0, LayoutParams.MATCH_PARENT, 10f))
        if(showDeepCB && path is MediaDir)
            addView(deepCB, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(removeBtn, LayoutParams(100, LayoutParams.WRAP_CONTENT))
    }
}
