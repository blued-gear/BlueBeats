package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.util.MediaDBEventRelay
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class FileBrowser(private val mediaDB: MediaDBEventRelay) : Fragment() {

    private lateinit var playerVM: PlayerViewModel
    private lateinit var mainVM: MainActivityViewModel
    private val listAdapter: ViewAdapter
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private lateinit var scanListener: MediaDB.ScanEventHandler
    private var currentDir: String = "/"

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment FileBrowser.
         */
        @JvmStatic
        fun newInstance() =
            FileBrowser(VlcManagers.getMediaDB()).apply {
                arguments = Bundle().apply {
                    // put args
                }
            }
    }

    init{
        val adapterRef = AtomicReference<ViewAdapter>(null)//XXX self-reference in init is not allowed
        listAdapter = ViewAdapter {
            if(it is MediaDir){
                currentDir = it.path
                expandMediaDir(it){
                    withContext(Dispatchers.Main){
                        adapterRef.get().setEntries(it)
                    }
                }
            }else if(it is MediaFile){
                mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
                playerVM.play(it)
            }
        }
        adapterRef.set(listAdapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.arguments.let{
            // read args
        }

        val vmProvider = ViewModelProvider(this.requireActivity())
        playerVM = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
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

        listView = view.findViewById(R.id.fb_entrylist)
        progressBar = view.findViewById(R.id.fb_progress)

        listView!!.layoutManager = LinearLayoutManager(this.requireContext())
        listView!!.adapter = listAdapter

        // add media-scan listener
        scanListener = object : MediaDB.ScanEventHandler(Handler(Looper.getMainLooper())){
            override fun handleScanStarted() {
                progressBar!!.isIndeterminate = true
            }
            override fun handleScanFinished() {
                progressBar!!.isIndeterminate = false
            }
            override fun handleNewNodeFound(node: MediaNode) {
                if(node.parent?.path == currentDir)
                    listAdapter.addEntry(node)
            }
            override fun handleNodeRemoved(node: MediaNode) {
                if(node.parent?.path == currentDir)
                    listAdapter.removeEntry(node)
            }
            override fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode) {

            }
            override fun handleScanException(e: Exception) {

            }
        }
        mediaDB.addSubscriber(scanListener)

        mediaDB.addSubscriber(object : MediaDB.ScanEventHandler(){// debug listener
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

        scanMedia()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        //TODO stop scan

        mediaDB.removeSubscriber(scanListener)

        listView = null
        progressBar = null
    }

    private fun scanMedia(){
        currentDir = "/"

        CoroutineScope(Dispatchers.IO).launch {
            mediaDB.getSubject().loadDB()

            // show root
            val mediaTreeRoot = mediaDB.getSubject().getMediaTreeRoot()
            expandMediaDir(mediaTreeRoot){
                withContext(Dispatchers.Main){
                    listAdapter.setEntries(it)
                }
            }

            mediaDB.getSubject().scanInAll()
        }
    }

    private class MediaNodeViewHolder(itemView: View, private val clickHandler: (MediaNode) -> Unit) : RecyclerView.ViewHolder(itemView) {

        lateinit var entry: MediaNode;

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