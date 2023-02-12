package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import com.anggrayudi.storage.extension.launchOnUiThread
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class FileBrowser(private val mediaDB: MediaDB) : Fragment() {

    private val listAdapter: ViewAdapter
    private var listView: RecyclerView? = null
    private var progressBar: ProgressBar? = null

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment FileBrowser.
         */
        @JvmStatic
        fun newInstance(mediaDB: MediaDB) =
            FileBrowser(mediaDB).apply {
                arguments = Bundle().apply {
                    // put args
                }
            }
    }

    init{
        val adapterRef = AtomicReference<ViewAdapter>(null)//XXX self-reference in init is not allowed
        listAdapter = ViewAdapter {
            if(it.type == MediaNode.Type.DIR){
                adapterRef.get().setEntries(expandMediaDir(it as MediaDir))
            }else{
                Log.d("MediaBrowser", "clicked file ${it.path}")
            }
        }
        adapterRef.set(listAdapter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.arguments.let{
            // read args
        }
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

        scanMedia()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        //TODO stop scan

        listView = null
        progressBar = null
    }

    private fun scanMedia(){
        progressBar!!.isIndeterminate = true

        GlobalScope.launch {
            withContext(Dispatchers.IO){
                //TODO get all roots
                val roots = arrayOf("/storage/3EB0-1BF2/Max/")
                mediaDB.scanInAll(roots[0])
                val mediaTree = mediaDB.getMediaTreeRoot()

                withContext(Dispatchers.Main){
                    listAdapter.setEntries(expandMediaDir(mediaTree))
                    progressBar!!.isIndeterminate = false
                }
            }
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
            this.itemView.findViewById<TextView>(R.id.v_mn_text).text = "${entry.type}: ${entry.name}"
        }

    }
    private class ViewAdapter(private val entryClickHandler: (MediaNode) -> Unit): RecyclerView.Adapter<MediaNodeViewHolder>() {

        private val entries: MutableList<MediaNode> = ArrayList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaNodeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.view_media_node, parent, false)
            return MediaNodeViewHolder(view, entryClickHandler)
        }

        override fun onBindViewHolder(holder: MediaNodeViewHolder, position: Int) {
            holder.setData(entries[position])
        }

        override fun getItemCount(): Int {
            return entries.size
        }

        fun setEntries(entries: List<MediaNode>){
            this.entries.clear()
            this.entries.addAll(entries)

            this.entries.sortWith{ a, b ->
                when {
                    (a.type == MediaNode.Type.DIR && b.type !== MediaNode.Type.DIR) -> -1
                    (b.type == MediaNode.Type.DIR && a.type !== MediaNode.Type.DIR) -> 1
                    else -> b.name.compareTo(a.name)
                }
            }

            this.notifyDataSetChanged()
        }
    }
}

private fun expandMediaDir(dir: MediaDir): List<MediaNode>{
    return listOf(dir.getChildren(), dir.getFiles()).flatten()
}