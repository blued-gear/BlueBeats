package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import kotlinx.coroutines.*

private const val STATE_PLAYLIST_ID = "key:plId"

internal class DynplaylistEditorFragment() : Fragment(R.layout.playlists_dyneditor_fragment) {

    private val playlistDao = RoomDB.DB_INSTANCE.dynamicPlaylistDao()
    private val playlistManager = RoomDB.DB_INSTANCE.playlistManager()

    private var playlist: DynamicPlaylist by OnceSettable()

    private var plName: TextView by OnceSettable()
    private var plBufferSize: EditText by OnceSettable()
    private var plRules: RecyclerView by OnceSettable()

    private var adapter: GenericFastItemAdapter by OnceSettable()

    constructor(playlist: DynamicPlaylist) : this() {
        this.playlist = playlist
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState !== null){
            val plId = savedInstanceState.getLong(STATE_PLAYLIST_ID)
            CoroutineScope(Dispatchers.IO).launch {
                playlist = playlistDao.load(plId)

                withContext(Dispatchers.Main) {
                    setupAdapter()
                }
            }
        }else{
            setupAdapter()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        runBlocking {
            withContext(Dispatchers.IO) {
                outState.putLong(STATE_PLAYLIST_ID, playlistManager.getPlaylistId(playlist.name))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        plName = view.findViewById(R.id.dyneditor_name)
        plBufferSize = view.findViewById(R.id.dyneditor_buffersize)
        plRules = view.findViewById(R.id.dyneditor_rules)

        setupRecyclerView()

        // catch all clicks
        view.setOnClickListener {}
    }

    override fun onStart() {
        super.onStart()
        showData()
    }

    private fun setupAdapter() {
        adapter = GenericFastItemAdapter()
        adapter.getExpandableExtension()
        adapter.add(createEditor(playlist.rootRuleGroup))
    }

    private fun setupRecyclerView() {
        plRules.layoutManager = LinearLayoutManager(this.context)
        plRules.adapter = adapter
    }

    private fun showData() {
        plName.text = playlist.name
        plBufferSize.setText(playlist.iterationSize.toString())
    }
}
