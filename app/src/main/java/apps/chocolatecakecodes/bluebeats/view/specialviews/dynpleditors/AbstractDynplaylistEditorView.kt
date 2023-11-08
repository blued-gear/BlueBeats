package apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import apps.chocolatecakecodes.bluebeats.view.specialviews.ExpandableCard
import io.github.esentsov.PackagePrivate

private const val SUBITEM_INSET = 40

@PackagePrivate
internal abstract class AbstractDynplaylistEditorView(ctx: Context) : FrameLayout(ctx) {

    protected val expander: ExpandableCard
    protected val contentList: LinearLayout
    val header: SimpleAddableRuleHeaderView

    init {
        contentList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        header = SimpleAddableRuleHeaderView(context)

        expander = ExpandableCard(context, header, contentList, true, SUBITEM_INSET).also {
            this.addView(it)
        }
    }
}
