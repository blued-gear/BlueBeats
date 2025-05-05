package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.Rule
import com.google.android.material.color.MaterialColors
import io.github.esentsov.PackagePrivate
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.truncate

@PackagePrivate
internal class ShareEditor(
    initialShare: Rule.Share,
    ctx: Context,
    private val onResult: (Rule.Share?) -> Unit
) : FrameLayout(ctx) {

    private enum class ShareMode { RELATIVE, EVEN, ABSOLUTE, UNLIMITED }

    private val relativeRb: RadioButton
    private val evenRb: RadioButton
    private val absoluteRb: RadioButton
    private val unlimitedRb: RadioButton
    private val valueInp: EditText
    private val okBtn: Button
    private val cancelBtn: Button

    init {
        setBackgroundColor(
            MaterialColors.getColor(this,
            android.R.attr.colorBackground, Color.MAGENTA))

        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            relativeRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_relative)
                isChecked = initialShare.isRelative && initialShare.value >= 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.RELATIVE)
                }
            }.also {
                addView(it)
            }
            evenRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_even)
                isChecked = initialShare.isRelative && initialShare.value < 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.EVEN)
                }
            }.also {
                addView(it)
            }
            absoluteRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_absolute)
                isChecked = !initialShare.isRelative && initialShare.value >= 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.ABSOLUTE)
                }
            }.also {
                addView(it)
            }
            unlimitedRb = RadioButton(ctx).apply {
                setText(R.string.dynpl_share_unlimited)
                isChecked = !initialShare.isRelative && initialShare.value < 0
                setOnCheckedChangeListener { _, checked ->
                    if(checked)
                        onShareModeChanged(ShareMode.UNLIMITED)
                }
            }.also {
                addView(it)
            }

            valueInp = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                setSingleLine()
                if(initialShare.isRelative){
                    inputType = inputType or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
                    val shareDecimal = DecimalFormatSymbols().apply {
                        decimalSeparator = '.'
                    }.let { formatSymbols ->
                        DecimalFormat("#.####", formatSymbols).apply {
                            roundingMode = RoundingMode.HALF_UP
                        }.format(initialShare.value)
                    }
                    text.append(shareDecimal)
                }else{
                    text.append(initialShare.value.toInt().toString())
                }

            }.also {
                addView(it)
            }

            okBtn = Button(context).apply {
                text = context.getString(R.string.misc_ok)
                setOnClickListener {
                    onOk()
                }
            }
            cancelBtn = Button(context).apply {
                text = context.getString(R.string.misc_cancel)
                setOnClickListener {
                    onCancel()
                }
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

    private fun onOk() {
        var shareVal = valueInp.text.toString().toFloat()
        if(unlimitedRb.isChecked) {
            onResult(Rule.Share(-1f, false))
        } else if(evenRb.isChecked) {
            onResult(Rule.Share(-1f, true))
        } else {
            val relative = relativeRb.isChecked
            if(relative)
                shareVal = shareVal.coerceAtMost(1f)
            onResult(
                Rule.Share(
                if(relative) shareVal else truncate(shareVal),
                relative
            ))
        }
    }

    private fun onCancel() {
        onResult(null)
    }

    private fun onShareModeChanged(mode: ShareMode) {
        when(mode) {
            ShareMode.RELATIVE -> {
                valueInp.isEnabled = true
                valueInp.inputType = valueInp.inputType or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL

                evenRb.isChecked = false
                absoluteRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.EVEN -> {
                valueInp.isEnabled = false

                relativeRb.isChecked = false
                absoluteRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.ABSOLUTE -> {
                valueInp.isEnabled = true
                valueInp.inputType = valueInp.inputType and EditorInfo.TYPE_NUMBER_FLAG_DECIMAL.inv()
                valueInp.text.apply {
                    val currentVal = this.toString().toFloat()
                    clear()
                    append(currentVal.toInt().toString())
                }

                relativeRb.isChecked = false
                evenRb.isChecked = false
                unlimitedRb.isChecked = false
            }
            ShareMode.UNLIMITED -> {
                valueInp.isEnabled = false

                relativeRb.isChecked = false
                evenRb.isChecked = false
                absoluteRb.isChecked = false
            }
        }
    }
}
