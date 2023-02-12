package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.media.playlist.StaticPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.drag.IDraggable
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.listeners.addTouchListener
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.*

private const val MNU_ID_OVERVIEW_RM = 11
private const val MNU_ID_OVERVIEW_NEW_DYN = 12
private const val MNU_ID_PLAYLIST_RM = 21
private const val MNU_ID_PLAYLIST_INFO = 22

internal class Playlists : Fragment() {

    companion object {
        fun newInstance() = Playlists()
    }

    private var viewModel: PlaylistsViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()
    private var itemsAdapter: GenericFastItemAdapter by OnceSettable()
    private var itemsTouchHelper: ItemTouchHelper by OnceSettable()
    private var itemsView = RequireNotNull<RecyclerView>()
    private var titleText = RequireNotNull<TextView>()
    private var upBtn = RequireNotNull<ImageButton>()
    private var menu: Menu? = null
    private var inSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlaylistsViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
        playerVM = vmProvider.get(PlayerViewModel::class.java)

        setupFastAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.playlists_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleText.set(view.findViewById(R.id.pls_title))
        upBtn.set(view.findViewById(R.id.pls_up_btn))

        setupRecycleView()
        wireActionHandlers()
        wireObservers()
    }

    override fun onResume() {
        super.onResume()

        mainVM.menuProvider.value = { menu, _ ->
            this.menu = menu
            buildMenuItems()
        }

        if(viewModel.showOverview.value == true)
            loadPlaylists(true)
        else
            loadPlaylistItems()
    }

    override fun onDestroyView() {
        itemsView.set(null)
        titleText.set(null)
        upBtn.set(null)
        itemsTouchHelper.attachToRecyclerView(null)

        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()

        menu = null
    }

    private fun setupFastAdapter() {
        itemsAdapter = GenericFastItemAdapter()

        val select = itemsAdapter.getSelectExtension()
        select.isSelectable = true
        select.selectOnLongClick = true
        select.allowDeselection = true
        select.multiSelect = true

        // prevent click-event when long-click for selection
        itemsAdapter.onLongClickListener = { _, _, _, _ ->
            true
        }

        setupItemDrag()
    }
    private fun setupItemDrag() {
        val actionCallback = object : ItemTouchCallback {
            override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
                itemsAdapter.move(oldPosition, newPosition)
                return true
            }
            override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
                if(oldPosition == newPosition) return
                onItemMoved(oldPosition, newPosition)
            }
        }

        val itemsDragCallback = SimpleDragCallback(actionCallback)
        itemsDragCallback.notifyAllDrops = false
        itemsDragCallback.isDragEnabled = false
        itemsTouchHelper = ItemTouchHelper(itemsDragCallback)

        itemsAdapter.addTouchListener<MediaItem.ViewHolder, GenericItem>(
            resolveView = { holder ->
                holder.itemView.findViewById(R.id.v_mf_handle)
            },
            onTouch = { v: View, event: MotionEvent, position: Int, fastAdapter: FastAdapter<GenericItem>, item: GenericItem ->
                if(event.action == MotionEvent.ACTION_DOWN) {
                    if(item is MediaItem && viewModel.selectedPlaylist is StaticPlaylist) {
                        itemsView.get().findViewHolderForAdapterPosition(position)?.let {
                            itemsTouchHelper.startDrag(it)
                        }

                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        )
    }

    private fun setupRecycleView() {
        val recyclerView = this.requireView().findViewById<RecyclerView>(R.id.pls_entries)
        recyclerView.layoutManager = LinearLayoutManager(this.requireContext(), LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = itemsAdapter
        itemsTouchHelper.attachToRecyclerView(recyclerView)

        itemsView.set(recyclerView)
    }

    //region action handlers
    private fun wireActionHandlers() {
        wireItemsClickListener()
        wireUpBtn()

        mainVM.addBackPressListener(this.viewLifecycleOwner, this::onBackPressed)

        itemsAdapter.getSelectExtension().selectionListener = object : ISelectionListener<GenericItem> {
            override fun onSelectionChanged(item: GenericItem, selected: Boolean) {
                onItemSelectionChanged(item, selected)
            }
        }
    }

    private fun wireItemsClickListener(){
        itemsAdapter.fastAdapter!!.onClickListener = { _, _, item, pos ->
            if(inSelection){
                itemsAdapter.getSelectExtension().toggleSelection(pos)
                true
            } else {
                if (viewModel.selectedPlaylist === null) {
                    onSelectPlaylist((item as ListsItem).playlist)
                } else {
                    onPlayPlaylistAt(pos)
                }

                false
            }
        }
    }
    private fun onSelectPlaylist(playlistInfo: PlaylistInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.selectedPlaylist = when (playlistInfo.second) {
                PlaylistType.STATIC -> RoomDB.DB_INSTANCE.staticPlaylistDao()
                    .load(playlistInfo.third)
                PlaylistType.DYNAMIC -> {
                    //TODO only for debugging; change to show elements
                    val pl = RoomDB.DB_INSTANCE.dynamicPlaylistDao().load(playlistInfo.third)
                    withContext(Dispatchers.Main) {
                        onEditDynamicPlaylist(pl)
                    }
                    return@launch
                }
            }

            viewModel.showOverview.postValue(false)
        }
    }
    private fun onPlayPlaylistAt(idx: Int) {
        val iter = viewModel.selectedPlaylist!!.getIterator(false, false)
        iter.seek(idx)

        mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
        playerVM.playPlaylist(iter)
    }

    private fun onItemSelectionChanged(item: GenericItem, selected: Boolean) {
        if(selected) {
            if(item is MediaItem){
                viewModel.selectedMedia = item.media
            } else {
                viewModel.selectedMedia = null
            }
        } else {
            // if only one selection is left (and it is a media) use it as selected file
            val selectedItems = itemsAdapter.getSelectExtension().selectedItems.toList()
            if(selectedItems.size == 1) {
                val selectedItem = selectedItems[0]
                if(selectedItem is MediaItem)
                    viewModel.selectedMedia = selectedItem.media
                else
                    viewModel.selectedMedia = null
            } else {
                viewModel.selectedMedia = null
            }
        }

        inSelection = itemsAdapter.getSelectExtension().selectedItems.isNotEmpty()

        updateMenu()
    }

    private fun onItemMoved(from: Int, to: Int) {
        val playlist = viewModel.selectedPlaylist!! as StaticPlaylist// only supported static playlist

        CoroutineScope(Dispatchers.IO).launch {
            playlist.moveMedia(from, to)
            RoomDB.DB_INSTANCE.staticPlaylistDao().save(playlist)
        }
    }

    private fun wireUpBtn() {
        upBtn.get().setOnClickListener {
            viewModel.showOverview.postValue(true)
        }
    }

    private fun onBackPressed() {
        if(mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS) {// close dialog
            this.parentFragmentManager.beginTransaction()
                .remove(this.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.FILE_DETAILS.tag)!!)
                .commit()

            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)
            updateMenu()
        } else if(mainVM.currentDialog.value == MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR){// close dialog
            this.parentFragmentManager.beginTransaction()
                .remove(this.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR.tag)!!)
                .commit()

            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)

            updateMenu()
            loadPlaylists(true)
        } else if(inSelection) {// deselect all
            itemsAdapter.getSelectExtension().deselect()
        } else if(viewModel.showOverview.value == false) {// go back to overview
            viewModel.showOverview.value = true
        }
    }

    private fun onEditDynamicPlaylist(playlist: DynamicPlaylist) {
        val dlg = DynplaylistEditorFragment(playlist)
        this.parentFragmentManager.beginTransaction()
            .add(R.id.main_content, dlg, MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR.tag)
            .commit()
        Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR)
    }
    //endregion

    private fun wireObservers() {
        viewModel.showOverview.observe(this.viewLifecycleOwner) {
            if(it) {
                showPlaylists()
            } else {
                showPlaylistItems()
            }

            buildMenuItems()
        }
    }

    private fun showPlaylists() {
        titleText.get().text = null
        upBtn.get().visibility = Button.INVISIBLE

        if(viewModel.allLists !== null)// already showing playlists
            return

        viewModel.playlistItems = null
        viewModel.selectedPlaylist = null
        loadPlaylists(false)
    }

    private fun showPlaylistItems() {
        titleText.get().text = viewModel.selectedPlaylist!!.name
        upBtn.get().visibility = Button.VISIBLE

        if(viewModel.playlistItems !== null)// already showing playlist-items
            return

        viewModel.allLists = null
        loadPlaylistItems()
    }

    private fun loadPlaylists(refresh: Boolean) {
        if(viewModel.allLists === null || refresh) {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.allLists = RoomDB.DB_INSTANCE.playlistManager().listAllPlaylist().map {
                    PlaylistInfo(it.key, it.value.first, it.value.second)
                }

                withContext(Dispatchers.Main) {
                    itemsAdapter.setNewList(viewModel.allLists!!.map {
                        ListsItem(it)
                    })

                    inSelection = false
                }
            }
        } else {
            itemsAdapter.setNewList(viewModel.allLists!!.map {
                ListsItem(it)
            })

            inSelection = false
        }
    }

    private fun loadPlaylistItems() {
        if(viewModel.playlistItems === null) {
            viewModel.playlistItems = viewModel.selectedPlaylist!!.items()
        }

        itemsAdapter.setNewList(viewModel.playlistItems!!.map {
            MediaItem(it)
        })

        inSelection = false
    }

    //region menu
    private fun buildMenuItems() {
        val menu = this.menu ?: return

        menu.clear()
        if(viewModel.showOverview.value == true) {
            buildMenuItemsForOverview(menu)
        } else {
            buildMenuItemsForPlaylist(menu)
        }

        updateMenu()
    }
    private fun buildMenuItemsForOverview(menu: Menu) {
        menu.add(Menu.NONE, MNU_ID_OVERVIEW_RM, Menu.NONE, R.string.misc_remove).apply {
            setOnMenuItemClickListener {
                onDeleteSelectedPlaylists()
                true
            }
        }

        menu.add(Menu.NONE, MNU_ID_OVERVIEW_NEW_DYN, Menu.NONE, R.string.playlists_add_dyn).apply {
            setOnMenuItemClickListener {
                onNewDynamicPlaylist()
                true
            }
        }
    }
    private fun buildMenuItemsForPlaylist(menu: Menu) {
        menu.add(Menu.NONE, MNU_ID_PLAYLIST_RM, Menu.NONE, R.string.misc_remove).apply {
            setOnMenuItemClickListener {
                onDeleteSelectedMedia()
                true
            }
        }

        menu.add(Menu.NONE, MNU_ID_PLAYLIST_INFO, Menu.NONE, R.string.filebrowser_menu_fileinfo).apply {
            setOnMenuItemClickListener {
                onShowMediaInfo()
                true
            }
        }
    }

    private fun updateMenu() {
        val menu = this.menu ?: return
        if(viewModel.showOverview.value == true) {
            updateOverviewMenu(menu)
        } else {
            updatePlaylistMenu(menu)
        }
    }
    private fun updateOverviewMenu(menu: Menu) {
        menu.findItem(MNU_ID_OVERVIEW_RM).apply {
            isEnabled = itemsAdapter.getSelectExtension().selectedItems.isNotEmpty()
        }
    }
    private fun updatePlaylistMenu(menu: Menu) {
        menu.findItem(MNU_ID_PLAYLIST_RM).apply {
            val staticPlaylist = viewModel.selectedPlaylist is StaticPlaylist
            val someSelected = itemsAdapter.getSelectExtension().selectedItems.isNotEmpty()
            isEnabled = staticPlaylist && someSelected
        }

        menu.findItem(MNU_ID_PLAYLIST_INFO).apply {
            val dialogOpen = mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS
            val mediaSelected = viewModel.selectedMedia !== null
            isEnabled = !dialogOpen && mediaSelected
        }
    }

    private fun onDeleteSelectedPlaylists() {
        CoroutineScope(Dispatchers.IO).launch {
            itemsAdapter.getSelectExtension().selectedItems.forEach {
                val listInfo = (it as ListsItem).playlist
                when(listInfo.second){
                    PlaylistType.STATIC -> RoomDB.DB_INSTANCE.staticPlaylistDao().delete(listInfo.third)
                    PlaylistType.DYNAMIC -> RoomDB.DB_INSTANCE.dynamicPlaylistDao().delete(listInfo.third)
                }
            }

            withContext(Dispatchers.Main) {
                loadPlaylists(true)
            }
        }
    }

    private fun onNewDynamicPlaylist() {
        val ctx = this.requireContext()
        CoroutineScope(Dispatchers.IO).launch {
            val existingPlaylist = RoomDB.DB_INSTANCE.playlistManager().listAllPlaylist().mapValues {
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
                    .setPositiveButton("OK") { dlg, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val plName = plSelect.text
                            val dao = RoomDB.DB_INSTANCE.dynamicPlaylistDao()

                            try {
                                val pl = dao.createNew(plName)
                                dlg.dismiss()
                                onEditDynamicPlaylist(pl)
                            }catch (e: Exception){
                                Toast.makeText(ctx,
                                    "A playlist with this name does already exist",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun onDeleteSelectedMedia() {
        CoroutineScope(Dispatchers.IO).launch {
            val playlist = viewModel.selectedPlaylist as StaticPlaylist
            itemsAdapter.getSelectExtension().selectedItems.map {
                itemsAdapter.getPosition(it)
            }.sorted().asReversed().forEach {// remove by index in reversed order so that I do not have to account for changed indexes
                playlist.removeMedia(it)
            }

            RoomDB.DB_INSTANCE.staticPlaylistDao().save(playlist)

            withContext(Dispatchers.Main) {
                loadPlaylistItems()
            }
        }
    }

    private fun onShowMediaInfo() {
        viewModel.selectedMedia!!.let {
            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.FILE_DETAILS)

            val dlg = FileDetails(it)
            this.parentFragmentManager.beginTransaction()
                .add(R.id.main_content, dlg, MainActivityViewModel.Dialogs.FILE_DETAILS.tag)
                .commit()

            updateMenu()
        }
    }
    //endregion
}

//region RecycleView-Items
private class ListsItem(val playlist: PlaylistInfo) : SelectableItem<ListsItem.ViewHolder>() {

    override val type: Int = R.layout.playlists_fragment * 100 + 1
    override val layoutRes: Int = R.layout.playlists_entry_list

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<ListsItem>(view) {

        private val name = this.itemView.findViewById<TextView>(R.id.pls_lists_name)

        override fun bindView(item: ListsItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            name.text = item.playlist.first
        }
        override fun unbindView(item: ListsItem) {
            super.unbindView(item)

            name.text = null
        }
    }
}

internal class MediaItem(val media: MediaFile, draggable: Boolean = true) : SelectableItem<MediaItem.ViewHolder>(), IDraggable {

    override val type: Int = R.layout.playlists_fragment * 100 + 2
    override val layoutRes: Int = R.layout.view_media_file

    override val isDraggable: Boolean = draggable

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<MediaItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mf_text)
        private val dragHandle: View = view.findViewById(R.id.v_mf_handle)

        override fun bindView(item: MediaItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            title.text = item.media.name
            dragHandle.visibility = if(item.isDraggable) View.VISIBLE else View.GONE
        }
        override fun unbindView(item: MediaItem) {
            super.unbindView(item)

            title.text = null
        }
    }
}
//endregion
