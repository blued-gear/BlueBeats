package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FileBrowser : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance() = FileBrowser()
    }

    private var viewModel: FileBrowserViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private lateinit var listAdapter: ViewAdapter
    private lateinit var mainMenu: Menu
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private lateinit var scanListener: MediaDB.ScanEventHandler
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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.filebrowser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup views
        listView = view.findViewById(R.id.fb_entrylist)
        progressBar = view.findViewById(R.id.fb_progress)

        setupAdapter(listView!!)

        listView!!.layoutManager = LinearLayoutManager(this.requireContext())

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

            updateMenuFileInfo()
        }
    }

    private fun wireObservers(){
        // add handler for back button (to get one dir up)
        mainVM.addBackPressListener(this.viewLifecycleOwner){
            // check if we can go up by one dir
            val parentDir = viewModel.currentDir.value!!.parent
            if (parentDir !== null) {
                viewModel.setCurrentDir(parentDir)
            }
        }

        viewModel.currentDir.observe(this.viewLifecycleOwner){
            if(it !== null){
                expandMediaDir(it) {
                    withContext(Dispatchers.Main) {
                        listAdapter.setEntries(it)

                        // scanMedia() was split because there were a race condition between this set and addEntry in the scan-listener
                        if(scanRequested)
                            scanMedia()
                    }
                }
            }
        }

        viewModel.storagePermissionsGranted.observe(this.viewLifecycleOwner){
            if(it)
                loadMediaRoot()
            else
                Toast.makeText(this.requireContext(), R.string.filebrowser_perm_needed, Toast.LENGTH_SHORT).show()
        }

        listAdapter.selectionTracker.addObserver(
            object : SelectionTracker.SelectionObserver<String>(){
                override fun onItemStateChanged(key: String, selected: Boolean) {
                    listView?.findViewHolderForAdapterPosition(listAdapter.keyProvider.getPosition(key))?.let{
                        val holder = it as MediaNodeViewHolder
                        holder.setIsSelected(selected)

                        val mediaNode = holder.entry
                        if(selected && mediaNode is MediaFile)
                            viewModel.selectFile(mediaNode)
                        else
                            viewModel.selectFile(null)
                    }

                    updateMenuFileInfo()
                }
            }
        )
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
                if(node.parent == viewModel.currentDir.value)
                    listAdapter.addEntry(node)
            }
            override fun handleNodeRemoved(node: MediaNode) {
                if(node.parent == viewModel.currentDir.value)
                    listAdapter.removeEntry(node)
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

    private fun setupAdapter(recycleView: RecyclerView){
        val entriesContainer = TreeSet<MediaNode> { a, b ->
            when {
                (a is MediaDir && b !is MediaDir) -> -1
                (b is MediaDir && a !is MediaDir) -> 1
                else -> b.name.compareTo(a.name)
            }
        }

        val mediaNodeKeyProvider = MediaNodeKeyProvider(entriesContainer)

        listAdapter = ViewAdapter(entriesContainer, mediaNodeKeyProvider) {
            if(it is MediaDir){
                viewModel.setCurrentDir(it)
                viewModel.selectFile(null)

                expandMediaDir(it){
                    withContext(Dispatchers.Main){
                        listAdapter.setEntries(it)
                    }
                }
            }else if(it is MediaFile){
                viewModel.selectFile(it)

                mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
                playerVM.play(it)
            }
        }.apply {
            setHasStableIds(true)
        }

        recycleView.adapter = listAdapter

        listAdapter.selectionTracker = SelectionTracker.Builder<String>(
            "fileSelection",
            recycleView,
            mediaNodeKeyProvider,
            MediaNodeDetailsLookup(recycleView),
            StorageStrategy.createStringStorage()
        )
            .withSelectionPredicate(SelectionPredicates.createSelectAnything())
            .build()

        viewModel.currentDir.value?.let {
            expandMediaDir(it) {
                listAdapter.setEntries(it)
            }
        }
    }

    private fun updateMenuFileInfo(){
        val chaptersItem = mainMenu.findItem(R.id.filebrowser_menu_details)
        chaptersItem.isEnabled = viewModel.selectedFile.value !== null
    }

    private fun onFileDetailsClicked(){
        viewModel.selectedFile.value?.let {
            val dlg = FileDetails(it)
            //dlg.show(this.requireActivity().supportFragmentManager, null)
            this.parentFragmentManager.beginTransaction()
                .add(R.id.main_content, dlg)
                .commit()
        }
    }

    //region recycle-view helper classes
    private class MediaNodeViewHolder(itemView: View, private val clickHandler: (MediaNode) -> Unit)
        : RecyclerView.ViewHolder(itemView) {

        lateinit var entry: MediaNode

        init{
            itemView.setOnClickListener {
                clickHandler(entry)
            }
        }

        fun setData(entry: MediaNode){
            this.entry = entry
            this.itemView.findViewById<TextView>(R.id.v_mn_text).text = "${entry.javaClass.simpleName}: ${entry.name}"
        }

        fun setIsSelected(selected: Boolean){
            if(selected)
                this.itemView.setBackgroundResource(R.color.selection_highlight)
            else
                this.itemView.setBackgroundResource(R.color.design_default_color_background)
        }
    }

    private class ViewAdapter(
        val elements: TreeSet<MediaNode>,
        val keyProvider: MediaNodeKeyProvider,
        private val entryClickHandler: (MediaNode) -> Unit
    ) : RecyclerView.Adapter<MediaNodeViewHolder>() {

        var selectionTracker: SelectionTracker<String> by OnceSettable()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaNodeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.view_media_node, parent, false)
            return MediaNodeViewHolder(view, entryClickHandler)
        }

        override fun onBindViewHolder(holder: MediaNodeViewHolder, position: Int) {
            holder.setData(elements.elementAt(position))
            holder.setIsSelected(selectionTracker.isSelected(mediaNodeSelectionKey(holder.entry)))
        }

        override fun getItemCount(): Int {
            return elements.size
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        fun setEntries(entries: List<MediaNode>){
            elements.clear()
            elements.addAll(entries)
            this.notifyDataSetChanged()
        }
        fun addEntry(entry: MediaNode){
            elements.add(entry)
            this.notifyDataSetChanged()
        }
        fun removeEntry(node: MediaNode){
            elements.remove(node)
            this.notifyDataSetChanged()
        }
    }

    private class MediaNodeKeyProvider(private val elements: TreeSet<MediaNode>)
        : ItemKeyProvider<String>(SCOPE_CACHED){

        override fun getKey(position: Int): String {
            return mediaNodeSelectionKey(elements.elementAt(position))
        }

        override fun getPosition(key: String): Int {
            return elements.indexOfFirst { mediaNodeSelectionKey(it) == key }
        }
    }

    private class MediaNodeDetailsLookup(private val recycleView: RecyclerView) : ItemDetailsLookup<String>(){
        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            val view = recycleView.findChildViewUnder(e.x, e.y)
            if(view === null)
                return null

            val holder = recycleView.getChildViewHolder(view) as MediaNodeViewHolder
            return object : ItemDetailsLookup.ItemDetails<String>(){
                override fun getPosition(): Int = holder.adapterPosition
                override fun getSelectionKey(): String = mediaNodeSelectionKey(holder.entry)
            }
        }
    }
    //endregion
}

private fun expandMediaDir(dir: MediaDir, next: suspend (List<MediaNode>) -> Unit){
    CoroutineScope(Dispatchers.IO).launch {
        next(listOf(dir.getDirs(), dir.getFiles()).flatten())
    }
}

private fun mediaNodeSelectionKey(entry: MediaNode) = entry.name
