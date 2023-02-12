package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.ExcludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.IncludeRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rulelike
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import com.skydoves.expandablelayout.ExpandableLayout

internal class DynplaylistGroupEditor(context: Context, private val group: RuleGroup) : LinearLayout(context) {

    private var expander: ExpandableLayout by OnceSettable()

    init {
        setupContent()
    }

    private fun setupContent() {
        this.addView(TextView(this.context).apply { text = "AAAAAAAAAA" })

        val header = LinearLayout(this.context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        header.addView(TextView(this.context).apply {
            text = "Group"//TODO groups should have names
        })
        header.addView(ImageButton(this.context).apply {
            setImageResource(R.drawable.ic_baseline_add_24)
            imageTintList = ColorStateList.valueOf(Color.BLACK)
            setOnClickListener {
                onAddClicked()
            }
        })

        val rulesLayout = LinearLayout(this.context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        showItems(rulesLayout)

        expander = ExpandableLayout(this.context).apply {
            parentLayout = header
            secondLayout = rulesLayout

            showSpinner = true
            spinnerAnimate = true
            duration = 100
        }
        this.addView(expander)
        expander.expand()

        this.addView(TextView(this.context).apply { text = "BBBBBBBBB" })
    }

    private fun showItems(target: LinearLayout) {
        group.getExcludes().forEach {
            target.addView(createEditor(it))
        }
        group.getRules().forEach {
            target.addView(createEditor(it))
        }

        target.addView(TextView(this.context).apply { text = "CCCCCCCCCCCCCCC" })
    }

    private fun createEditor(item: Rulelike): View {
        return when(item) {
            is ExcludeRule -> DynplaylistExcludesEditor(this.context, item)
            is IncludeRule -> DynplaylistIncludesEditor(this.context, item)
            else -> throw IllegalArgumentException("unsupported rule")
        }
    }

    private fun onAddClicked() {
        //TODO
    }
}

internal class DynplaylistExcludesEditor(context: Context, rule: ExcludeRule) : FrameLayout(context) {

}

internal class DynplaylistIncludesEditor(context: Context, rule: IncludeRule) : FrameLayout(context) {

}
