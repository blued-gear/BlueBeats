package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
    private var listAdapter: ViewAdapter by OnceSettable()
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private lateinit var scanListener: MediaDB.ScanEventHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(FileBrowserViewModel::class.java)
        playerVM = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

        setupAdapter()

        scanMedia()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_file_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup views
        listView = view.findViewById(R.id.fb_entrylist)
        progressBar = view.findViewById(R.id.fb_progress)

        listView!!.layoutManager = LinearLayoutManager(this.requireContext())
        listView!!.adapter = listAdapter

        wireObservers()
        wireScanListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        //TODO stop scan

        viewModel.mediaDB.removeSubscriber(scanListener)

        listView = null
        progressBar = null
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
                    }
                }
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

    private fun scanMedia(){
        if(viewModel.mediaWasScanned)
            return

        CoroutineScope(Dispatchers.IO).launch {
            val mediaDB = viewModel.mediaDB.getSubject()

            mediaDB.loadDB()

            viewModel.setCurrentDir(mediaDB.getMediaTreeRoot())

            viewModel.mediaScanned()// call before scanInAll to prevent calls to scanMedia() before scan was complete

            mediaDB.scanInAll()
        }
    }

    private fun setupAdapter(){
        listAdapter = ViewAdapter {
            if(it is MediaDir){
                viewModel.setCurrentDir(it)
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
        }
    }

    private class MediaNodeViewHolder(itemView: View, private val clickHandler: (MediaNode) -> Unit) : RecyclerView.ViewHolder(itemView) {

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

    }
    private class ViewAdapter(private val entryClickHandler: (MediaNode) -> Unit): RecyclerView.Adapter<MediaNodeViewHolder>() {

        private val entries: MutableSet<MediaNode>

        init{
            entries = TreeSet { a, b ->
                when {
                    (a is MediaDir && b !is MediaDir) -> -1
                    (b is MediaDir && a !is MediaDir) -> 1
                    else -> b.name.compareTo(a.name)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaNodeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.view_media_node, parent, false)
            return MediaNodeViewHolder(view, entryClickHandler)
        }

        override fun onBindViewHolder(holder: MediaNodeViewHolder, position: Int) {
            holder.setData(entries.elementAt(position))
        }

        override fun getItemCount(): Int {
            return entries.size
        }

        fun setEntries(entries: List<MediaNode>){
            this.entries.clear()
            this.entries.addAll(entries)
            this.notifyDataSetChanged()
        }
        fun addEntry(entry: MediaNode){
            entries.add(entry)
            this.notifyDataSetChanged()
        }
        fun removeEntry(node: MediaNode){
            entries.remove(node)
            this.notifyDataSetChanged()
        }
    }
}

private fun expandMediaDir(dir: MediaDir, next: suspend (List<MediaNode>) -> Unit){
    CoroutineScope(Dispatchers.IO).launch {
        next(listOf(dir.getDirs(), dir.getFiles()).flatten())
    }
}