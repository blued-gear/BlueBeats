package apps.chocolatecakecodes.bluebeats.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    companion object {
        internal const val INTENT_OPTION_TAB = "open_optn-tab"

        private const val STORAGE_PERM_REQ_ID = 11
    }

    private lateinit var viewModel: MainActivityViewModel
    private lateinit var mainContentView: View
    private lateinit var mainTabContent: ViewPager2
    private lateinit var mainTabAdapter: TabContentAdapter

    private var appMenu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // init these fist as super.onCreate() might trigger access to them when a some fragments get restored
        RoomDB.init(this)
        if(!VlcManagers.isInitialized())
            VlcManagers.init(this)

        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.main_activity)

        mainContentView = this.findViewById(R.id.main_content)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(mainContentView) { v, insets ->
            val innerPadding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                innerPadding.left,
                innerPadding.top,
                innerPadding.right,
                innerPadding.bottom)
            return@setOnApplyWindowInsetsListener insets
        }

        setupTabs()
        setupSystemBarsHider()
        wireObservers()

        getAppPermissions()

        showRequestedTab()
    }

    override fun onPause() {
        super.onPause()

        viewModel.currentTab.value = MainActivityViewModel.Tabs.entries[mainTabContent.currentItem]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        appMenu = menu
        super.onCreateOptionsMenu(menu)

        val menuProvider = viewModel.menuProvider.value
        if(menuProvider !== null)
            menuProvider(menu, this.menuInflater)

        return true
    }

    private fun getAppPermissions() {
        val permissions = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val requestPermissions = permissions.any {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (requestPermissions) {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                STORAGE_PERM_REQ_ID
            )
        }else{
            val fbVM = ViewModelProvider(this).get(FileBrowserViewModel::class.java)
            fbVM.storagePermissionsGranted.postValue(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode != STORAGE_PERM_REQ_ID) return

        var grantSuccessful = true
        for(res in grantResults){
            if(res != PackageManager.PERMISSION_GRANTED){
                grantSuccessful = false
                break
            }
        }

        val fbVM = ViewModelProvider(this).get(FileBrowserViewModel::class.java)
        fbVM.storagePermissionsGranted.postValue(grantSuccessful)
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
            mainTabContent.setCurrentItem(it.ordinal, false)
        }

        viewModel.fullScreenContent.observe(this){
            if(it !== null){
                showFullscreenContent(it)
            }else{
                resetFullscreen()
            }
        }

        viewModel.menuProvider.observe(this){
            val appMenu = this.appMenu
            if(appMenu === null)
                return@observe

            appMenu.clear()
            if(it !== null)
                it(appMenu, this.menuInflater)
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

    private fun showRequestedTab() {
        if(this.intent.hasExtra(INTENT_OPTION_TAB)) {
            val tabs = MainActivityViewModel.Tabs.entries
            val idx = this.intent.getIntExtra(INTENT_OPTION_TAB, -1)
                .coerceAtLeast(0)
                .coerceAtMost(tabs.size)
            viewModel.currentTab.postValue(tabs[idx])
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
            Log.e("MainActivity", "can not show fullscreen-content: still attached to a parent")
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
            Log.e("MainActivity", "can not show main-content: still attached to a parent")
        }
    }

    private inner class TabContentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle): FragmentStateAdapter(fragmentManager, lifecycle) {

        val tabs: List<Pair<String, () -> Fragment>>

        init {
            tabs = MainActivityViewModel.Tabs.entries.map{
                when(it){
                    MainActivityViewModel.Tabs.MEDIA ->
                        Pair(getText(R.string.main_tab_media).toString(), { FileBrowser.newInstance() })
                    MainActivityViewModel.Tabs.SEARCH ->
                        Pair(getText(R.string.main_tab_search).toString(), { Search.newInstance() })
                    MainActivityViewModel.Tabs.PLAYER ->
                        Pair(getText(R.string.main_tab_player).toString(), { Player.newInstance() })
                    MainActivityViewModel.Tabs.PLAYLISTS ->
                        Pair(getText(R.string.main_tab_playlists).toString(), { Playlists.newInstance() })
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
