package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.noties.markwon.Markwon
import java.io.BufferedReader
import java.io.InputStreamReader

internal object AboutPopup {

    fun show(ctx: Context, anchor: ViewGroup, backPressDispatcher: OnBackPressedDispatcher) {
        Utils.showPopup(ctx, anchor, R.layout.about_view, false) { contentView ->
            val tabs = contentView.findViewById<TabLayout>(R.id.about_tabs)
            val pager = contentView.findViewById<ViewPager2>(R.id.about_pager)

            val adapter = PagerAdapter(ctx)
            pager.adapter = adapter

            TabLayoutMediator(tabs, pager) { tab, pos ->
                tab.text = adapter.contents[pos].first
            }.attach()
        }.let { popup ->
            val onBackPressed = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    this.remove()
                    popup.dismiss()
                }
            }
            backPressDispatcher.addCallback(onBackPressed)
        }
    }

    private class PagerAdapter(ctx: Context) : RecyclerView.Adapter<PagerAdapter.ViewHolder>() {

        val contents = listOf(
            "About" to readRes(ctx, R.raw.about_text_about),
            "Licenses" to readRes(ctx, R.raw.about_text_licenses)
        )

        private val md = Markwon.create(ctx)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val scrollView = ScrollView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val contentView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                scrollView.addView(this)
            }

            return ViewHolder(scrollView, contentView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            md.setMarkdown(holder.content, contents[position].second)
        }

        override fun getItemCount(): Int {
            return contents.size
        }

        private fun readRes(ctx: Context, id: Int) = BufferedReader(InputStreamReader(ctx.resources.openRawResource(id))).use {
            it.readText()
        }

        private class ViewHolder(view: View, val content: TextView) : RecyclerView.ViewHolder(view)
    }
}
