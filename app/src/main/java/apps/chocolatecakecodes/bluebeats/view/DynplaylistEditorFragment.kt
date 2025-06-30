package apps.chocolatecakecodes.bluebeats.view

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.GenericRule
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.dynamicplaylist.rules.RuleGroup
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.serializers.RuleGroupParcel
import apps.chocolatecakecodes.bluebeats.view.specialviews.dynpleditors.DynPlaylistEditors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

    private var mainVm: MainActivityViewModel by OnceSettable()
    private var myMnu: (Menu, MenuInflater) -> Unit by OnceSettable()
    private var orgMnu: ((Menu, MenuInflater) -> Unit)? = null
    private var mnuItemReset: MenuItem? = null

    private var modified: Boolean = false

    constructor(playlist: DynamicPlaylist) : this() {
        this.playlist = playlist
        this.editCopy = playlist.rootRuleGroup.copy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainVm = ViewModelProvider(this.requireActivity()).get(MainActivityViewModel::class.java)

        if(savedInstanceState !== null) {
            modified = savedInstanceState.getBoolean(STATE_MODIFIED)
            val plId = savedInstanceState.getLong(STATE_PLAYLIST_ID)
            CoroutineScope(Dispatchers.IO).launch {
                playlist = playlistDao.load(plId)
                val editCopyParcel = if (Build.VERSION.SDK_INT >= 33) {
                    savedInstanceState.getParcelable(STATE_RULES, RuleGroupParcel::class.java)!!
                } else {
                    savedInstanceState.getParcelable(STATE_RULES)!!
                }
                editCopy = editCopyParcel.content
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_MODIFIED, modified)
        runBlocking {
            withContext(Dispatchers.IO) {
                outState.putLong(STATE_PLAYLIST_ID, playlistManager.getPlaylistId(playlist.name))
                outState.putParcelable(STATE_RULES, RuleGroupParcel(editCopy))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        plRules = view.findViewById(R.id.dyneditor_rules)

        plBufferSize = view.findViewById<EditText>(R.id.dyneditor_buffersize).apply {
            this.doAfterTextChanged {
                if(it.toString() != playlist.name)
                    mnuItemReset?.isEnabled = true
            }
        }
        plName = view.findViewById<EditText>(R.id.dyneditor_name).apply {
            this.doAfterTextChanged {
                if(it.toString() != playlist.name)
                    mnuItemReset?.isEnabled = true
            }
        }

        myMnu = { mnu, _ ->
            buildMenu(mnu)
        }

        showData()

        mnuItemReset?.isEnabled = modified

        // catch all clicks
        view.setOnClickListener {}
    }

    override fun onResume() {
        super.onResume()

        orgMnu = mainVm.menuProvider.value
        mainVm.menuProvider.value = myMnu
    }

    override fun onPause() {
        super.onPause()

        if(mainVm.menuProvider.value == myMnu)
            mainVm.menuProvider.value = orgMnu
    }

    suspend fun saveChanges(): Boolean {
        if(!checkRuleShares(editCopy)) {
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

        if(saveSuccessful) {
            withContext(Dispatchers.Main) {
                mnuItemReset?.isEnabled = false
            }
        }

        return saveSuccessful
    }

    private fun showData() {
        plName.text.clear()
        plName.text.append(playlist.name)

        plBufferSize.setText(playlist.iterationSize.toString())

        plRules.removeAllViews()
        plRules.addView(
            DynPlaylistEditors.createEditorRoot(editCopy, this::onRuleEdited, this.requireContext()),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun buildMenu(menu: Menu) {
        mnuItemReset = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.dynpl_edit_mnu_reset).apply {
            setOnMenuItemClickListener {
                onResetRules()
                true
            }
            isEnabled = false
        }
    }

    private fun onRuleEdited(rule: GenericRule) {
        modified = true
        mnuItemReset?.isEnabled = true
    }

    private fun onResetRules() {
        plName.text.clear()
        plName.text.append(playlist.name)

        CoroutineScope(Dispatchers.IO).launch {
            editCopy.applyFrom(playlist.rootRuleGroup)

            modified = false
            withContext(Dispatchers.Main) {
                showData()
                mnuItemReset?.isEnabled = false
            }
        }
    }

    private fun checkRuleShares(group: RuleGroup): Boolean {
        group.getRules().filterNot {
            it.second
        }.map {
            it.first.share
        }.let { shares ->
            if(!shares.any { it.modeEven() }) {// check if all relative rules add up to 100%
                shares.filter {
                    it.modeRelative()
                }.let {
                    if(it.isNotEmpty()) {
                        val epsilon = 0.0001f
                        @Suppress("NAME_SHADOWING")
                        val shareSum = it.fold(0f) { acc, it ->
                            acc + it.value
                        }
                        if(abs(1f - shareSum) > epsilon)
                            return false
                    }
                }
            } else if(shares.any { it.modeEven() }) {// check if all relative rules add up to <= 100%
                shares.filter {
                    it.modeRelative()
                }.let {
                    if(it.isNotEmpty()) {
                        @Suppress("NAME_SHADOWING")
                        val shareSum = it.fold(0f) { acc, it ->
                            acc + it.value
                        }
                        if(shareSum > 1.0)
                            return false
                    }
                }
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
