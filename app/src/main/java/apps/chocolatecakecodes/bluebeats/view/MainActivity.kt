package apps.chocolatecakecodes.bluebeats.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

class MainActivity : AppCompatActivity() {

    private val STORAGE_PER_REQ_ID = 11
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var viewModel: MainActivityViewModel
    private lateinit var mainContentView: View
    private lateinit var mainTabContent: ViewPager2
    private lateinit var mainTabAdapter: TabContentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_main)

        mainContentView = this.findViewById(R.id.main_content)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        getAppPermissions()

        RoomDB.init(this)

        if(!VlcManagers.isInitialized())
            VlcManagers.init(this)

        wireObservers()
        listMediaRoots()
        setupTabs()
        setupSystemBarsHider()
    }

    override fun onBackPressed() {
        viewModel.onBackPressed()
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
        mainTabContent = this.findViewById(R.id.main_pager)
        mainTabAdapter = TabContentAdapter(this.supportFragmentManager, this.lifecycle)

        mainTabContent.adapter = mainTabAdapter

        tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        TabLayoutMediator(tabLayout, mainTabContent){ tab, pos ->
            tab.text = mainTabAdapter.tabs[pos].first
        }.attach()
    }

    private fun wireObservers(){
        viewModel.currentTab.observe(this){
            mainTabContent.currentItem = it.ordinal
        }

        viewModel.fullScreenContent.observe(this){
            if(it !== null){
                showFullscreenContent(it)
            }else{
                resetFullscreen()
            }
        }
    }

    private fun setupSystemBarsHider() {
        val decorView = this.window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.setOnApplyWindowInsetsListener { v, insets ->
                if(insets.isVisible(WindowInsets.Type.statusBars())
                    and (viewModel.fullScreenContent.value !== null)){
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(Duration.ofSeconds(3).toMillis())

                        launch(Dispatchers.Main) {
                            val insetsController = this@MainActivity.window.insetsController!!
                            // check if still in fullscreen
                            if (viewModel.fullScreenContent.value !== null)
                                insetsController.hide(WindowInsets.Type.systemBars())
                        }
                    }
                }

                return@setOnApplyWindowInsetsListener v.onApplyWindowInsets(insets)
            }
        } else {
            decorView.setOnSystemUiVisibilityChangeListener {
                if(((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    and (viewModel.fullScreenContent.value !== null)){
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(3000)

                        launch(Dispatchers.Main){
                            // check if still in fullscreen
                            if (viewModel.fullScreenContent.value !== null)
                                this@MainActivity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        }
                    }
                }
            }
        }
    }

    private fun showFullscreenContent(content: View){
        // make fullscreen
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            val insetsController = this.window.insetsController!!
            insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            insetsController.hide(WindowInsets.Type.systemBars())
        }else{
            this.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        this.supportActionBar?.hide()

        // show fullscreen-content
        if(content.parent === null) {
            this.setContentView(content)
        }else{
            Log.w("MainActivity", "can not show fullscreen-content: still attached to a parent")
        }
    }

    private fun resetFullscreen(){
        // undo fullscreen
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            this.window.insetsController!!.show(WindowInsets.Type.systemBars())
        }else{
            this.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        this.supportActionBar?.show()

        // show main-content
        if(mainContentView.parent === null) {
            this.setContentView(mainContentView)
        }else{
            Log.w("MainActivity", "can not show main-content: still attached to a parent")
        }
    }

    private fun listMediaRoots(){
        VlcManagers.getMediaDB().getSubject().addScanRoot("/storage/3EB0-1BF2/")
        //TODO list all available roots
    }

    private inner class TabContentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle): FragmentStateAdapter(fragmentManager, lifecycle) {

        val tabs: List<Pair<String, () -> Fragment>>

        init{
            tabs = MainActivityViewModel.Tabs.values().map{
                when(it){
                    MainActivityViewModel.Tabs.MEDIA -> Pair(getText(R.string.main_tab_media).toString(), {FileBrowser.newInstance()})
                    MainActivityViewModel.Tabs.PLAYER -> Pair(getText(R.string.main_tab_player).toString(), {Player.newInstance()})
                }
            }
        }

        override fun getItemCount(): Int {
            return tabs.size
        }

        override fun createFragment(position: Int): Fragment {
            return tabs[position].second()
        }
    }
}
