package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.os.Build
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
            "About" to readRes(ctx, "about_text_about.html"),
            "Licenses" to readRes(ctx, "open_source_licenses.html")
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val scrollView = ScrollView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val contentView = WebView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    isForceDarkAllowed = true
                if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, true)

                scrollView.addView(this)
            }

            return ViewHolder(scrollView, contentView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.content.loadData(contents[position].second, "text/html", "base64")
        }

        override fun getItemCount(): Int {
            return contents.size
        }

        private fun readRes(ctx: Context, filename: String) = BufferedReader(InputStreamReader(ctx.assets.open(filename))).use {
            it.readText().let {
                Base64.encodeToString(it.encodeToByteArray(), Base64.NO_PADDING)
            }
        }

        private class ViewHolder(view: View, val content: WebView) : RecyclerView.ViewHolder(view)
    }
}
