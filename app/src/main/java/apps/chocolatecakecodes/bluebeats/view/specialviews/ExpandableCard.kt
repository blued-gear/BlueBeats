package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.updatePadding
import apps.chocolatecakecodes.bluebeats.R
import net.cachapa.expandablelayout.ExpandableLayout

internal class ExpandableCard(
    context: Context,
    header: View,
    content: View,
    private val border: Boolean = false,
    private val inset: Int = 0
) : FrameLayout(context) {

    companion object {
        private val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private lateinit var header: View
    private lateinit var content: View
    private val innerContainer = LinearLayout(context)
    private val contentContainer = ExpandableLayout(context)

    var expanded: Boolean
        get() = contentContainer.isExpanded
        set(value){
            contentContainer.isExpanded = value
        }

    init {
        innerContainer.apply {
            orientation = LinearLayout.VERTICAL
            if(border)
                setBackgroundResource(R.drawable.shape_border)
            addView(contentContainer, lp)
        }
        contentContainer.apply {
            orientation = ExpandableLayout.VERTICAL
            updatePadding(left = inset)
        }

        setHeader(header)
        setContent(content)

        this.addView(innerContainer, lp)
    }

    fun getHeader(): View {
        return header
    }

    fun setHeader(view: View) {
        header = view
        if(innerContainer.childCount > 1)
            innerContainer.removeViewAt(0)
        innerContainer.addView(view, 0, lp)
        view.setOnClickListener {
            contentContainer.toggle()
        }
    }

    fun getContent(): View {
        return content
    }

    fun setContent(view: View) {
        content = view
        contentContainer.removeAllViews()
        contentContainer.addView(view, lp)
    }
}
