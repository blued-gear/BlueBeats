package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.SimpleObservable
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

private const val FILE_DETAILS_DLG_TAG = "dlg-file_details"

class FileBrowser : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = FileBrowser()
    }

    private var viewModel: FileBrowserViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private lateinit var listAdapter: GenericFastItemAdapter
    private lateinit var mainMenu: Menu
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private lateinit var scanListener: MediaDB.ScanEventHandler
    private var scanRequested = false
    private var inSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(FileBrowserViewModel::class.java)
        playerVM = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

        setupAdapter()
        listAdapter.withSavedInstanceState(savedInstanceState)//TODO this has to be called after the items were restored
    }

    override fun onSaveInstanceState(outState: Bundle) {
        listAdapter.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.filebrowser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup views
        val listView: RecyclerView = view.findViewById(R.id.fb_entrylist)
        listView.layoutManager = LinearLayoutManager(this.requireContext())
        listView.adapter = listAdapter

        this.listView = listView
        progressBar = view.findViewById(R.id.fb_progress)

        wireObservers()
        wireScanListeners()
    }

    override fun onResume() {
        super.onResume()

        setupMainMenu()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        //TODO stop scan

        viewModel.mediaDB.removeSubscriber(scanListener)

        listView = null
        progressBar = null
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
        // add handler for back button (to get one dir up)
        mainVM.addBackPressListener(this.viewLifecycleOwner){
            onBackPressed()
        }

        viewModel.currentDir.observe(this.viewLifecycleOwner){
            if(it !== null){
                expandMediaDir(it) {
                    withContext(Dispatchers.Main) {
                        listAdapter.setNewList(it)
                    }

                    // scanMedia() was split because there were a race condition between this set and addEntry in the scan-listener
                    if(scanRequested)
                        scanMedia()
                }
            }
        }

        viewModel.storagePermissionsGranted.observe(this.viewLifecycleOwner){
            if(it)
                loadMediaRoot()
            else
                Toast.makeText(this.requireContext(), R.string.filebrowser_perm_needed, Toast.LENGTH_SHORT).show()
        }

        listAdapter.getSelectExtension().selectionListener = object : ISelectionListener<GenericItem> {
            override fun onSelectionChanged(item: GenericItem, selected: Boolean) {
                onItemSelectionChanged(item, selected)
            }
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
                        if(!listAdapter.adapterItems.contains(it))
                            listAdapter.add(it)
                    }
                }
            }
            override fun handleNodeRemoved(node: MediaNode) {
                if(node.parent == viewModel.currentDir.value){
                    mediaNodeToItem(node)?.let {
                        listAdapter.remove(listAdapter.getPosition(it))
                    }
                }
            }
            override fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode) {

            }
            override fun handleScanException(e: Exception) {

            }
        }
        viewModel.mediaDB.addSubscriber(scanListener)

        viewModel.mediaDB.addSubscriber(object : MediaDB.ScanEventHandler(){// debug listener
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

    private fun setupAdapter(){
        listAdapter = GenericFastItemAdapter()
        listAdapter.setHasStableIds(true)

        val select = listAdapter.getSelectExtension()
        select.isSelectable = true
        select.allowDeselection = true
        select.multiSelect = true
        select.selectOnLongClick = true

        listAdapter.onClickListener = { _, _, item, pos ->
            onItemClick(item, pos)
        }
        // prevent click-event when long-click for selection
        listAdapter.onLongClickListener = { _, _, _, _ ->
            true
        }

        viewModel.currentDir.value?.let {
            expandMediaDir(it) {
                withContext(Dispatchers.Main) {
                    listAdapter.setNewList(it)
                }
            }
        }
    }

    private fun updateMenuItems(){
        val fileInfoItem = mainMenu.findItem(R.id.filebrowser_menu_details)
        val dialogOpen = mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS
        fileInfoItem.isEnabled = viewModel.selectedFile.value !== null
                && !dialogOpen

        val addToPlItem = mainMenu.findItem(R.id.filebrowser_menu_atp)
        val selectedItems = listAdapter.getSelectExtension().selectedItems
        val onlyFilesSelected = selectedItems.filterIsInstance<FileItem>()
            .size == listAdapter.getSelectExtension().selectedItems.size
        addToPlItem.isEnabled = selectedItems.isNotEmpty() && onlyFilesSelected
    }

    //region action-handlers
    private fun onBackPressed() {
        if(mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS){// close dialog
            this.parentFragmentManager.beginTransaction()
                .remove(this.parentFragmentManager.findFragmentByTag(FILE_DETAILS_DLG_TAG)!!)
                .commit()

            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)
            updateMenuItems()
        }else if(inSelection){// clear selection
            listAdapter.getSelectExtension().deselect()
        } else {// go one dir up
            // check if we can go up by one dir
            val parentDir = viewModel.currentDir.value!!.parent
            if (parentDir !== null) {
                viewModel.setCurrentDir(parentDir)
            }
        }
    }

    private fun onItemSelectionChanged(item: GenericItem, selected: Boolean) {
        if(selected) {
            if(item is FileItem){
                viewModel.selectFile(item.file)
            } else {
                viewModel.selectFile(null)
            }
        } else {
            val selectedItems = listAdapter.getSelectExtension().selectedItems.toList()
            if(selectedItems.size == 1) {
                // if only one selection is left (and it is a file) use it as selected file
                val selectedItem = selectedItems[0]
                if(selectedItem is FileItem)
                    viewModel.selectFile(selectedItem.file)
                else
                    viewModel.selectFile(null)
            } else {
                viewModel.selectFile(null)
            }
        }

        updateMenuItems()

        inSelection = listAdapter.getSelectExtension().selectedItems.isNotEmpty()
    }

    private fun onItemClick(item: GenericItem, pos: Int): Boolean {
        if(inSelection){
            listAdapter.getSelectExtension().toggleSelection(pos)
            return true
        }

        return when (item) {
            is DirItem -> {
                viewModel.setCurrentDir(item.dir)
                viewModel.selectFile(null)

                expandMediaDir(item.dir) {
                    withContext(Dispatchers.Main) {
                        listAdapter.setNewList(it)
                    }
                }

                true
            }
            is FileItem -> {
                viewModel.selectFile(item.file)

                mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
                playerVM.play(item.file)

                true
            }
            else -> {
                false
            }
        }
    }

    private fun onFileDetailsClicked(){
        viewModel.selectedFile.value?.let {
            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.FILE_DETAILS)

            val dlg = FileDetails(it)
            this.parentFragmentManager.beginTransaction()
                .add(R.id.main_content, dlg, FILE_DETAILS_DLG_TAG)
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
                //TODO create custom view with combination of TextEdit and Spinner
                val plSelect = AutoCompleteTextView(ctx).apply {
                    setAdapter(ArrayAdapter(
                        ctx,
                        R.layout.support_simple_spinner_dropdown_item,
                        existingPlaylist.keys.toList()
                    ))
                    threshold = 0
                }

                AlertDialog.Builder(ctx)
                    .setTitle(R.string.filebrowser_menu_add_playlist)
                    .setView(plSelect)
                    .setNegativeButton(R.string.misc_cancel) { dlg, _ ->
                        dlg.cancel()
                    }
                    .setPositiveButton("OK") { dlg, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val plName = plSelect.text.toString()
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
                                listAdapter.getSelectExtension().selectedItems
                                    .filterIsInstance<FileItem>().map {
                                        it.file
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

//region RecycleView-Items
private fun mediaNodeToItem(node: MediaNode) = when(node) {
    is MediaDir -> DirItem(node)
    is MediaFile -> FileItem(node)
    else -> null
}

private class DirItem(val dir: MediaDir) : SelectableItem<DirItem.ViewHolder>() {

    override val layoutRes: Int = R.layout.view_media_node
    override val type: Int = R.layout.filebrowser_fragment.shl(4) + 1
    override var identifier: Long = Objects.hash(type, dir).toLong()

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<DirItem>(view) {

        private val text: TextView = view.findViewById(R.id.v_mn_text)

        override fun bindView(item: DirItem, payloads: List<Any>) {
            super.bindView(item, payloads)
            text.text = "Dir: " + item.dir.name
        }
    }
}

private class FileItem(val file: MediaFile) : SelectableItem<FileItem.ViewHolder>() {

    override val layoutRes: Int = R.layout.view_media_node
    override val type: Int = R.layout.filebrowser_fragment.shl(4) + 2
    override var identifier: Long = Objects.hash(type, file).toLong()

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<FileItem>(view) {

        private val text: TextView = view.findViewById(R.id.v_mn_text)

        override fun bindView(item: FileItem, payloads: List<Any>) {
            super.bindView(item, payloads)
            text.text = "File: " + item.file.name
        }
    }
}
//endregion

private fun expandMediaDir(dir: MediaDir, next: suspend (List<AbstractItem<*>>) -> Unit){
    CoroutineScope(Dispatchers.IO).launch {
        dir.getDirs().sortedBy {
            it.name
        }.let { sortedDirs ->
            dir.getFiles().sortedBy {
                it.name
            }.let { sortedFiles ->
                listOf(sortedDirs, sortedFiles).flatten().mapNotNull {
                    mediaNodeToItem(it)
                }
            }
        }.let {
            next(it)
        }
    }
}
