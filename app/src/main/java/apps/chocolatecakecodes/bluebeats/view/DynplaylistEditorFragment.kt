package apps.chocolatecakecodes.bluebeats.view

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.Rulelike
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.view.specialviews.createEditor
import kotlinx.coroutines.*

private const val STATE_PLAYLIST_ID = "key:plId"
private const val STATE_MODIFIED = "key:mod"

internal class DynplaylistEditorFragment() : Fragment(R.layout.playlists_dyneditor_fragment) {

    private val playlistDao = RoomDB.DB_INSTANCE.dynamicPlaylistDao()
    private val playlistManager = RoomDB.DB_INSTANCE.playlistManager()

    private var playlist: DynamicPlaylist by OnceSettable()

    private var plName: TextView by OnceSettable()
    private var plBufferSize: EditText by OnceSettable()
    private var plRules: LinearLayout by OnceSettable()

    private var modified: Boolean = false

    constructor(playlist: DynamicPlaylist) : this() {
        this.playlist = playlist
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState !== null){
            modified = savedInstanceState.getBoolean(STATE_MODIFIED)
            val plId = savedInstanceState.getLong(STATE_PLAYLIST_ID)
            CoroutineScope(Dispatchers.IO).launch {
                playlist = playlistDao.load(plId)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_MODIFIED, modified)
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

        // catch all clicks
        view.setOnClickListener {}
    }

    override fun onStart() {
        super.onStart()
        showData()
    }

    override fun onStop() {
        super.onStop()

        if(modified) {
            CoroutineScope(Dispatchers.IO).launch {
                RoomDB.DB_INSTANCE.dynamicPlaylistDao().save(playlist)
                modified = false
            }
        }
    }

    private fun showData() {
        plName.text = playlist.name
        plBufferSize.setText(playlist.iterationSize.toString())
        plRules.addView(
            createEditor(playlist.rootRuleGroup, this::onRuleEdited, this.requireContext()),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun onRuleEdited(rule: Rulelike) {
        modified = true
    }
}
