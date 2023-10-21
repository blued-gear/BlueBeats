package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.text.InputType
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.addTextChangedListener
import apps.chocolatecakecodes.bluebeats.util.Utils
import java.util.concurrent.TimeUnit

typealias ChangedListener = (Long?) -> Unit

internal class TimeEdit(ctx: Context) : FrameLayout(ctx) {

    private val input = AppCompatEditText(ctx)
    private val listeners = ArrayList<ChangedListener>()

    init {
        this.addView(input)

        input.inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME

        input.addTextChangedListener(afterTextChanged = {
            val time = getTime()
            listeners.forEach {
                it(time)
            }
        })
    }

    /**
     * @return time in milliseconds or null if none was entered or it was invalid
     */
    fun getTime(): Long? {
        val text = this.input.text.toString()

        if(text.isEmpty())
            return null

        return parseTime(text)
    }

    /**
     * @param time time in milliseconds
     */
    fun setTime(time: Long) {
        val text = formatTime(time)
        input.text!!.let {
            it.replace(0, it.length, text)
        }
    }

    fun addChangedListener(listener: ChangedListener) {
        listeners.add(listener)
    }

    fun removeChangedListener(listener: ChangedListener) {
        listeners.remove(listener)
    }

    private fun parseTime(str: String): Long? {
        val parts = str.split(':')

        when (parts.size) {
            2 -> {// mm:ss.ms
                val m = parts[0].toLongOrNull() ?: return null
                val s = parts[1].toDoubleOrNull() ?: return null

                return (TimeUnit.MINUTES.toMillis(m)
                        + TimeUnit.SECONDS.toMillis(1) * s).toLong()
            }
            3 -> {// hh:mm:ss.ms
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val s = parts[2].toDoubleOrNull() ?: return null

                return (TimeUnit.HOURS.toMillis(h)
                        + TimeUnit.MINUTES.toMillis(m)
                        + TimeUnit.SECONDS.toMillis(1) * s).toLong()
            }
            else -> {
                return null
            }
        }
    }

    private fun formatTime(time: Long): String {
        return Utils.formatTime(time, true)
    }
}
