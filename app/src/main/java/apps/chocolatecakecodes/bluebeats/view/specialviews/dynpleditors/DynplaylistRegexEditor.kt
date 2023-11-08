package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.R
import androidx.core.widget.addTextChangedListener
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RegexRule
import apps.chocolatecakecodes.bluebeats.util.Debouncer
import io.github.esentsov.PackagePrivate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.PatternSyntaxException

@PackagePrivate
internal class DynplaylistRegexEditor(
    val rule: RegexRule,
    private val changedCallback: ChangedCallback,
    ctx: Context
) : AbstractDynplaylistEditorView(ctx) {

    val typeSelect = Spinner(ctx)
    val typeSelectAdapter = ArrayAdapter<String>(ctx, R.layout.support_simple_spinner_dropdown_item)
    val invalidRegexIndicator = TextView(ctx)

    val regexTextDebouncer = Debouncer.create<String>(50) {
        onRegexChanged(it)
    }

    init {
        header.title.text = ctx.getString(apps.chocolatecakecodes.bluebeats.R.string.dynpl_regex_title)

        header.setupShareEdit(rule.share) {
            rule.share = it
            changedCallback(rule)
        }

        TextView(ctx).apply {
            text = ctx.getText(apps.chocolatecakecodes.bluebeats.R.string.dynpl_regex_attr)
        }.let {
            this.contentList.addView(it)
        }

        typeSelect.adapter = typeSelectAdapter
        typeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                onTypeSelected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                throw AssertionError()
            }
        }
        this.contentList.addView(typeSelect)

        loadTypeValues()
        typeSelect.setSelection(rule.attribute.ordinal)

        TextView(ctx).apply {
            text = ctx.getText(apps.chocolatecakecodes.bluebeats.R.string.dynpl_regex_regex)
        }.let {
            this.contentList.addView(it)
        }

        EditText(ctx).apply {
            setSingleLine()
            text.append(rule.regex)

            addTextChangedListener {
                if (it != null)
                    regexTextDebouncer.debounce(it.toString())
            }
        }.let {
            this.contentList.addView(it)
        }

        invalidRegexIndicator.apply {
            setTextColor(Color.RED)
            setTextSize(TypedValue.COMPLEX_UNIT_FRACTION_PARENT, 12f)
            setPadding(32, 0, 0, 0)
        }.let {
            this.contentList.addView(it)
        }
    }

    fun onTypeSelected(idx: Int) {
        rule.attribute = RegexRule.Attribute.values()[idx]
        changedCallback(rule)
    }

    fun onRegexChanged(regex: String) {
        try {
            Regex(regex)
        } catch(ignored: PatternSyntaxException) {
            CoroutineScope(Dispatchers.Main).launch {
                invalidRegexIndicator.text = context.getText(apps.chocolatecakecodes.bluebeats.R.string.dynpl_regex_regex_invalid)
            }
            return
        }

        rule.regex = regex
        CoroutineScope(Dispatchers.Main).launch {
            invalidRegexIndicator.text = ""
            changedCallback(rule)
        }
    }

    private fun loadTypeValues() {
        RegexRule.Attribute.values().map {
            it.name
        }.map { // capitalize
            it.lowercase().replaceFirstChar {
                if (it.isLowerCase())
                    it.titlecase(Locale.getDefault())
                else
                    it.toString()
            }
        }.let {
            typeSelectAdapter.addAll(it)
        }
    }
}
