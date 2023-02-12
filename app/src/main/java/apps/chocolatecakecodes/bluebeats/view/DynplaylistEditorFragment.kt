package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.GenericRule
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.RuleGroup
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.view.specialviews.createEditorRoot
import kotlinx.coroutines.*
import java.lang.Exception
import kotlin.math.abs

private const val LOG_TAG = "DynplaylistEditor"
private const val STATE_PLAYLIST_ID = "key:plId"
private const val STATE_RULES = "key:rules"
private const val STATE_MODIFIED = "key:mod"

internal class DynplaylistEditorFragment() : Fragment(R.layout.playlists_dyneditor_fragment) {

    private val playlistDao = RoomDB.DB_INSTANCE.dynamicPlaylistDao()
    private val playlistManager = RoomDB.DB_INSTANCE.playlistManager()

    private var playlist: DynamicPlaylist by OnceSettable()
    private var editCopy: RuleGroup by OnceSettable()

    private var plName: EditText by OnceSettable()
    private var plBufferSize: EditText by OnceSettable()
    private var plRules: LinearLayout by OnceSettable()

    private var modified: Boolean = false

    constructor(playlist: DynamicPlaylist) : this() {
        this.playlist = playlist
        this.editCopy = playlist.rootRuleGroup.copy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState !== null){
            modified = savedInstanceState.getBoolean(STATE_MODIFIED)
            val plId = savedInstanceState.getLong(STATE_PLAYLIST_ID)
            CoroutineScope(Dispatchers.IO).launch {
                playlist = playlistDao.load(plId)
                editCopy = if (Build.VERSION.SDK_INT >= 33) {
                    savedInstanceState.getParcelable(STATE_RULES, RuleGroup::class.java)!!
                }else{
                    savedInstanceState.getParcelable(STATE_RULES)!!
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_MODIFIED, modified)
        runBlocking {
            withContext(Dispatchers.IO) {
                outState.putLong(STATE_PLAYLIST_ID, playlistManager.getPlaylistId(playlist.name))
                outState.putParcelable(STATE_RULES, editCopy)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        plName = view.findViewById(R.id.dyneditor_name)
        plBufferSize = view.findViewById(R.id.dyneditor_buffersize)
        plRules = view.findViewById(R.id.dyneditor_rules)

        showData()

        // catch all clicks
        view.setOnClickListener {}
    }

    suspend fun saveChanges(): Boolean {
        if(!checkRuleShares(playlist.rootRuleGroup)){
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.dynpl_edit_rules_not_100, Toast.LENGTH_LONG).show()
            }
            return false
        }

        var saveSuccessful = true
        if(plName.text.toString() != playlist.name)
            saveSuccessful = saveSuccessful and savePlName()
        if(modified || plBufferSize.text.toString() != playlist.iterationSize.toString())
            saveSuccessful = saveSuccessful and savePlData()
        return saveSuccessful
    }

    private fun showData() {
        plName.text.clear()
        plName.text.append(playlist.name)
        plBufferSize.setText(playlist.iterationSize.toString())
        plRules.addView(
            createEditorRoot(editCopy, this::onRuleEdited, this.requireContext()),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun onRuleEdited(rule: Rule) {
        modified = true
    }

    private fun checkRuleShares(group: RuleGroup): Boolean{
        group.getRules().filterNot {
            it.second
        }.map {
            it.first.share
        }.filter {
            it.isRelative
        }.let {
            if(it.isNotEmpty()){
                val epsilon = 0.0001f
                @Suppress("NAME_SHADOWING")
                val shareSum = it.fold(0f) { acc, it ->
                    acc + it.value
                }
                if(abs(1f - shareSum) > epsilon)
                    return false
            }
        }

        group.getRules().filterNot {
            it.second
        }.map {
            it.first
        }.filterIsInstance<RuleGroup>().forEach {
            if(!checkRuleShares(it))
                return false
        }

        return true
    }

    private suspend fun savePlName(): Boolean {
        val newName = plName.text.toString()

        if(newName.isBlank()){
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.dynpl_edit_name_invalid_blank, Toast.LENGTH_LONG).show()
            }

            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                RoomDB.DB_INSTANCE.dynamicPlaylistDao().changeName(playlist, newName)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.dynpl_edit_name_saved, Toast.LENGTH_SHORT).show()
                }

                true
            }catch (e: Exception) {
                Log.e(LOG_TAG, "exception while changing playlist-name", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.dynpl_edit_name_not_saved, Toast.LENGTH_LONG).show()
                }

                false
            }
        }
    }

    private suspend fun savePlData(): Boolean {
        val ctx = context
        playlist.iterationSize = plBufferSize.text.toString().toInt()

        return withContext(Dispatchers.IO) {
            try {
                playlist.rootRuleGroup.applyFrom(editCopy)

                RoomDB.DB_INSTANCE.dynamicPlaylistDao().save(playlist)
                modified = false

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, R.string.misc_saved, Toast.LENGTH_SHORT).show()
                }

                true
            }catch (e: Exception) {
                Log.e(LOG_TAG, "exception while saving playlist", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, R.string.dynpl_edit_save_unsuccessful, Toast.LENGTH_SHORT).show()
                }

                false
            }
        }
    }
}
