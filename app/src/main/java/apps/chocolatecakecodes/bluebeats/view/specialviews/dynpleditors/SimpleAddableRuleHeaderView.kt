package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.updatePaddingRelative
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Share
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate
import java.math.RoundingMode
import java.text.DecimalFormat

@PackagePrivate
internal class SimpleAddableRuleHeaderView(ctx: Context) : LinearLayout(ctx) {

    object CommonVisuals {

        fun addButton(ctx: Context) = AppCompatImageButton(ctx).apply {
            setImageResource(R.drawable.ic_baseline_add_24)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(this,
                android.R.attr.colorForeground, Color.BLACK))
        }

        fun negateCheckbox(ctx: Context) = CheckBox(ctx).apply {
            text = "negate"
        }

        fun logicButton(ctx: Context) = LogicButton(ctx)

        class LogicButton(ctx: Context) : AppCompatImageButton(ctx) {

            init {
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(this,
                    android.R.attr.colorForeground, Color.BLACK))
            }

            fun setLogicMode(and: Boolean) {
                if(and)
                    this.setImageResource(R.drawable.ic_intersect)
                else
                    this.setImageResource(R.drawable.ic_union)
            }
        }
    }

    val title = TextView(context)
    val name = EditText(context)
    val shareBtn = Button(context)

    private lateinit var ruleShare: Share

    init {
        this.orientation = HORIZONTAL

        title.updatePaddingRelative(
            top = Utils.dpToPx(ctx, 4),
            start = Utils.dpToPx(ctx, 4),
            end = Utils.dpToPx(ctx, 8)
        )
        name.setSingleLine()

        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(name, LayoutParams(0, LayoutParams.MATCH_PARENT, 5f))
    }

    /**
     * @param atEnd if true item will be placed as the last view; else it will be placed after the share-btn
     */
    fun addVisual(item: View, atEnd: Boolean) {
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f)
        if(atEnd)
            this.addView(item, lp)
        else
            this.addView(item, 2, lp)
    }

    fun setupShareEdit(initialShare: Share, onEdited: (Share) -> Unit) {
        ruleShare = initialShare
        setShareBtnText(initialShare)
        shareBtn.setOnClickListener(onEditShareHandler(onEdited))

        if(shareBtn.parent === null)
            addVisual(shareBtn, true)
    }

    @SuppressLint("SetTextI18n")
    private fun setShareBtnText(share: Share) {
        if(share.modeRelative()) {
            val sharePercentage = DecimalFormat("#.#").apply {
                roundingMode = RoundingMode.HALF_UP
                multiplier = 100
            }.format(share.value)
            shareBtn.text = "S: ${sharePercentage}%"
        } else if(share.modeAbsolute()) {
            shareBtn.text = "S: ${share.value.toInt()}"
        } else if(share.modeEven()) {
            shareBtn.text = "evn"
        } else if (share.modeUnlimited()) {
            shareBtn.text = "inf"
        }
    }

    private fun onEditShareHandler(onEditedHandler: (Share) -> Unit): OnClickListener {
        return OnClickListener{
            var popup: PopupWindow? = null
            val popupContent = ShareEditor(ruleShare, context) { newVal ->
                newVal?.let {
                    if(it != ruleShare) {
                        onEditedHandler(it)
                        ruleShare = it
                        setShareBtnText(it)
                    }
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
}
