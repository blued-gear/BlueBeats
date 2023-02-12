package apps.chocolatecakecodes.bluebeats.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.VlcPlayerManager
import apps.chocolatecakecodes.bluebeats.util.MediaDBEventRelay
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private val STORAGE_PER_REQ_ID = 11
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private lateinit var vlcMng: VlcPlayerManager
    private lateinit var mediaMng: MediaDB
    private lateinit var mediaMngEventRelay: MediaDBEventRelay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_main)

        getAppPermissions()

        RoomDB.init(this)

        vlcMng = VlcPlayerManager(this)
        mediaMngEventRelay = MediaDBEventRelay()
        mediaMng = MediaDB(vlcMng.libVlc, mediaMngEventRelay)
        mediaMngEventRelay.setSubject(mediaMng)

        listMediaRoots()
        setupTabs()
    }

    private fun getAppPermissions(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_STORAGE,
                STORAGE_PER_REQ_ID
            )
        }
    }

    private fun setupTabs(){
        val tabLayout: TabLayout = this.findViewById(R.id.main_tabs)
        val tabContent: ViewPager2 = this.findViewById(R.id.main_pager)
        val tabAdapter = TabContentAdapter(this.supportFragmentManager, this.lifecycle)

        tabContent.adapter = tabAdapter

        tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        TabLayoutMediator(tabLayout, tabContent){ tab, pos ->
            tab.text = tabAdapter.tabs[pos].first
        }.attach()
    }

    private fun listMediaRoots(){
        mediaMng.addScanRoot("/storage/3EB0-1BF2/")
        //TODO list all available roots
    }

    private inner class TabContentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle): FragmentStateAdapter(fragmentManager, lifecycle) {

        val tabs: List<Pair<String, () -> Fragment>>

        init{
            tabs = listOf(
                Pair("Media", {FileBrowser.newInstance(mediaMngEventRelay)})
            )
        }

        override fun getItemCount(): Int {
            return tabs.size
        }

        override fun createFragment(position: Int): Fragment {
            return tabs[position].second()
        }
    }
}