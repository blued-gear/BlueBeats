package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.taglib.BuildConfig
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.SmartBackPressedCallback
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.view.specialviews.FileBrowserView
import apps.chocolatecakecodes.bluebeats.view.specialviews.SpinnerTextbox
import apps.chocolatecakecodes.bluebeats.view.specialviews.mediaNodeToItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileBrowser : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = FileBrowser()
    }

    private var viewModel: FileBrowserViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private lateinit var mainMenu: Menu
    private var progressBar: ProgressBar? = null
    private lateinit var scanListener: MediaDB.ScanEventHandler
    private var browser: FileBrowserView by OnceSettable()
    private var scanRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(FileBrowserViewModel::class.java)
        playerVM = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.filebrowser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        browser = FileBrowserView(this.requireContext())
        view.findViewById<FrameLayout>(R.id.fb_entrylist).addView(browser)
        progressBar = view.findViewById(R.id.fb_progress)

        this.requireActivity().onBackPressedDispatcher.addCallback(SmartBackPressedCallback(this.lifecycle, this::onBackPressed))

        setupBrowser()
        wireObservers()
        wireScanListeners()
    }

    override fun onResume() {
        super.onResume()

        setupMainMenu()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.mediaDB.removeSubscriber(scanListener)

        progressBar = null
    }

    private fun setupBrowser() {
        browser.apply {
            notifyClickOnDir = true
            notifyClickOnSelection = false

            itemClickListener = this@FileBrowser::onItemClick
            itemSelectionChangedListener = this@FileBrowser::onItemSelectionChanged

            setSelectable(true, true, true)
        }
    }

    private fun setupMainMenu(){
        mainVM.menuProvider.value = { menu, menuInflater ->
            mainMenu = menu

            menuInflater.inflate(R.menu.file_browser_menu, menu)

            menu.findItem(R.id.filebrowser_menu_details).setOnMenuItemClickListener {
                onFileDetailsClicked()
                true
            }
            menu.findItem(R.id.filebrowser_menu_atp).setOnMenuItemClickListener {
                onAddToPlClicked()
                true
            }

            updateMenuItems()
        }
    }

    private fun wireObservers(){
        viewModel.currentDir.observe(this.viewLifecycleOwner){
            if(it !== null){
                browser.currentDir = it

                // scanMedia() was split because there were a race condition between this set and addEntry in the scan-listener
                if(scanRequested)
                    scanMedia()
            }
        }

        viewModel.storagePermissionsGranted.observe(this.viewLifecycleOwner){
            if(it)
                loadMediaRoot()
            else
                Toast.makeText(this.requireContext(), R.string.filebrowser_perm_needed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun wireScanListeners(){
        scanListener = object : MediaDB.ScanEventHandler(Handler(Looper.getMainLooper())){
            override fun handleScanStarted() {
                progressBar!!.isIndeterminate = true
            }
            override fun handleScanFinished() {
                progressBar!!.isIndeterminate = false
            }
            override fun handleNewNodeFound(node: MediaNode) {
                if(node.parent == viewModel.currentDir.value) {
                    mediaNodeToItem(node)?.let {
                        if(!browser.listAdapter.adapterItems.contains(it))
                            browser.listAdapter.add(it)
                    }
                }
            }
            override fun handleNodeRemoved(node: MediaNode) {
                if(node.parent == viewModel.currentDir.value){
                    mediaNodeToItem(node)?.let {
                        browser.listAdapter.remove(browser.listAdapter.getPosition(it))
                    }
                }
            }
            override fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode) {

            }
            override fun handleScanException(e: Exception) {

            }
        }
        viewModel.mediaDB.addSubscriber(scanListener)

        if(BuildConfig.DEBUG) {
            viewModel.mediaDB.addSubscriber(object : MediaDB.ScanEventHandler() {
                // debug listener
                override fun handleScanStarted() {
                    Log.d("FileBrowser", "scan started")
                }

                override fun handleScanFinished() {
                    Log.d("FileBrowser", "scan finished")
                }

                override fun handleNewNodeFound(node: MediaNode) {
                    Log.d("FileBrowser", "new node found: ${node.path}")
                }

                override fun handleNodeRemoved(node: MediaNode) {
                    Log.d("FileBrowser", "node removed: ${node.path}")
                }

                override fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode) {
                    Log.d("FileBrowser", "node changed: ${node.path}")
                }

                override fun handleScanException(e: Exception) {
                    Log.e("FileBrowser", "exception in scan", e)
                }
            })
        }
    }

    private fun loadMediaRoot(){
        if(viewModel.mediaWasScanned)
            return

        CoroutineScope(Dispatchers.IO).launch {
            val mediaDB = viewModel.mediaDB.getSubject()

            mediaDB.loadDB()

            scanRequested = true
            viewModel.setCurrentDir(mediaDB.getMediaTreeRoot())
        }
    }
    private fun scanMedia(){
        if(viewModel.mediaWasScanned)
            return

        scanRequested = false

        CoroutineScope(Dispatchers.IO).launch {
            val mediaDB = viewModel.mediaDB.getSubject()

            viewModel.mediaScanned()// call before scanInAll to prevent calls to scanMedia() before scan was complete

            mediaDB.scanInAll()
        }
    }

    private fun updateMenuItems(){
        val fileInfoItem = mainMenu.findItem(R.id.filebrowser_menu_details)
        val dialogOpen = mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS
        fileInfoItem.isEnabled = viewModel.selectedFile.value !== null
                && !dialogOpen

        val addToPlItem = mainMenu.findItem(R.id.filebrowser_menu_atp)
        val selectedItems = browser.selectedItems
        val onlyFilesSelected = selectedItems.filterIsInstance<MediaFile>().size == selectedItems.size
        addToPlItem.isEnabled = selectedItems.isNotEmpty() && onlyFilesSelected
    }

    //region action-handlers
    private fun onBackPressed() {
        if(mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS){// close dialog
            this.parentFragmentManager.beginTransaction()
                .remove(this.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.FILE_DETAILS.tag)!!)
                .commit()

            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)
            updateMenuItems()
        }else if(browser.inSelection){// clear selection
            browser.clearSelection()
        } else {// go one dir up
            browser.goDirUp()
        }
    }

    private fun onItemSelectionChanged(item: MediaNode, selected: Boolean) {
        if(selected) {
            if(item is MediaFile){
                viewModel.selectFile(item)
            } else {
                viewModel.selectFile(null)
            }
        } else {
            val selectedItems = browser.selectedItems
            if(selectedItems.size == 1) {
                // if only one selection is left (and it is a file) use it as selected file
                val selectedItem = selectedItems[0]
                if(selectedItem is MediaFile)
                    viewModel.selectFile(selectedItem)
                else
                    viewModel.selectFile(null)
            } else {
                viewModel.selectFile(null)
            }
        }

        updateMenuItems()
    }

    private fun onItemClick(item: MediaNode) {
        when (item) {
            is MediaDir -> {
                viewModel.setCurrentDir(item)
                viewModel.selectFile(null)
            }
            is MediaFile -> {
                viewModel.selectFile(item)

                mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
                playerVM.play(item)
            }
        }
    }

    private fun onFileDetailsClicked(){
        viewModel.selectedFile.value?.let {
            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.FILE_DETAILS)

            val dlg = FileDetails(it)
            this.parentFragmentManager.beginTransaction()
                .add(R.id.main_content, dlg, MainActivityViewModel.Dialogs.FILE_DETAILS.tag)
                .commit()

            updateMenuItems()
        }
    }

    private fun onAddToPlClicked(){
        val ctx = this.requireContext()
        CoroutineScope(Dispatchers.IO).launch {
            val existingPlaylist = RoomDB.DB_INSTANCE.playlistManager().listAllPlaylist().filter {
                it.value.first == PlaylistType.STATIC
            }.mapValues {
                it.value.second
            }

            withContext(Dispatchers.Main) {
                val plSelect = SpinnerTextbox(ctx).apply {
                    setItems(existingPlaylist.keys.toList())
                }

                AlertDialog.Builder(ctx)
                    .setTitle(R.string.filebrowser_menu_add_playlist)
                    .setView(plSelect)
                    .setNegativeButton(R.string.misc_cancel) { dlg, _ ->
                        dlg.cancel()
                    }
                    .setPositiveButton(R.string.misc_ok) { dlg, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val plName = plSelect.text
                            val dao = RoomDB.DB_INSTANCE.staticPlaylistDao()

                            val pl = if(existingPlaylist.containsKey(plName)){
                                dao.load(existingPlaylist[plName]!!)
                            } else {
                                try {
                                    dao.createNew(plName)
                                }catch (e: Exception){
                                    Toast.makeText(ctx,
                                        "A playlist with this name does already exist",
                                        Toast.LENGTH_LONG).show()

                                    null
                                }
                            }

                            if(pl !== null) {
                                browser.selectedItems
                                    .filterIsInstance<MediaFile>().map {
                                        it
                                    }.sortedBy {
                                        it.name
                                    }.forEach {
                                        pl.addMedia(it)
                                    }

                                dao.save(pl)

                                withContext(Dispatchers.Main) {
                                    dlg.dismiss()
                                }
                            }
                        }
                    }.show()
            }
        }
    }
    //endregion
}
