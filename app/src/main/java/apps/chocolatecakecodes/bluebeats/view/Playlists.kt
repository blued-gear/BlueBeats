package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.media.playlist.StaticPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist.DynamicPlaylistIterator
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import apps.chocolatecakecodes.bluebeats.util.SmartBackPressedCallback
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.castTo
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import apps.chocolatecakecodes.bluebeats.view.specialitems.SelectableItem
import apps.chocolatecakecodes.bluebeats.view.specialviews.SpinnerTextbox
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.listeners.addTouchListener
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val LOG_TAG = "Playlists"
private const val MNU_ID_OVERVIEW_RM = 11
private const val MNU_ID_OVERVIEW_NEW_DYN = 12
private const val MNU_ID_OVERVIEW_EDIT_DYN = 13
private const val MNU_ID_PLAYLIST_RM = 21
private const val MNU_ID_PLAYLIST_INFO = 22
private const val MNU_ID_PLAYLIST_EDIT_DYN = 23

internal class Playlists : Fragment() {

    companion object {
        fun newInstance() = Playlists()
    }

    private var viewModel: PlaylistsViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var itemsAdapter: GenericFastItemAdapter by OnceSettable()
    private var itemsTouchHelper: ItemTouchHelper by OnceSettable()
    private var itemsView = RequireNotNull<RecyclerView>()
    private var titleText = RequireNotNull<TextView>()
    private var upBtn = RequireNotNull<ImageButton>()
    private var menu: Menu? = null
    private var inSelection = false

    private val player: VlcPlayer
        get() = requireActivity().castTo<MainActivity>().playerConn.player!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlaylistsViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

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
            loadPlaylistItems(false)
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

        itemsAdapter.addTouchListener<MediaFileItem.ViewHolder, GenericItem>(
            resolveView = { holder ->
                holder.itemView.findViewById(R.id.v_mf_handle)
            },
            onTouch = { _: View, event: MotionEvent, position: Int, _: FastAdapter<GenericItem>, item: GenericItem ->
                if(event.action == MotionEvent.ACTION_DOWN) {
                    if(item is MediaFileItem && viewModel.selectedPlaylist is StaticPlaylist) {
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

        this.requireActivity().onBackPressedDispatcher.addCallback(SmartBackPressedCallback(this.lifecycle, this::onBackPressed))

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
                if (viewModel.inOverview) {
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
                PlaylistType.DYNAMIC -> RoomDB.DB_INSTANCE.dynamicPlaylistDao()
                    .load(playlistInfo.third)
            }

            viewModel.showOverview.postValue(false)
        }
    }
    private fun onPlayPlaylistAt(idx: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val iter = viewModel.selectedPlaylist!!.getIterator(false, false)
            if(iter is DynamicPlaylistIterator) {
                iter.seekToMedia(itemsAdapter.getItem(idx)!!.castTo<MediaFileItem>().file)
            } else {
                iter.seek(idx)
            }

            mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
            player.playPlaylist(iter)
        }
    }

    private fun onItemSelectionChanged(item: GenericItem, selected: Boolean) {
        if(selected) {
            if(item is MediaFileItem){
                viewModel.selectedMedia = item.file
            } else {
                viewModel.selectedMedia = null
            }
        } else {
            // if only one selection is left (and it is a media) use it as selected file
            val selectedItems = itemsAdapter.getSelectExtension().selectedItems.toList()
            if(selectedItems.size == 1) {
                val selectedItem = selectedItems[0]
                if(selectedItem is MediaFileItem)
                    viewModel.selectedMedia = selectedItem.file
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
            onCloseEditDynplDlg()
        } else if(inSelection) {// deselect all
            itemsAdapter.getSelectExtension().deselect()
        } else if(viewModel.showOverview.value == false) {// go back to overview
            viewModel.showOverview.value = true
        }
    }

    private fun onEditDynamicPlaylist(playlist: DynamicPlaylist) {
        val editor = DynplaylistEditorFragment(playlist)
        this.parentFragmentManager.beginTransaction()
            .add(R.id.main_content, editor, MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR.tag)
            .commit()

        Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR)

        menu = null// will be handled by editor
    }

    private fun onCloseEditDynplDlg() {
        CoroutineScope(Dispatchers.Unconfined).launch {
            val editor = this@Playlists.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR.tag) as DynplaylistEditorFragment
            if(editor.saveChanges()){
                withContext(Dispatchers.Main) {
                    this@Playlists.parentFragmentManager.beginTransaction()
                        .remove(this@Playlists.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.DYNPLAYLIST_EDITOR.tag)!!)
                        .commit()

                    Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)

                    updateMenu()
                    if(!viewModel.inOverview)
                        loadPlaylistItems(true)
                    else
                        loadPlaylists(true)
                }
            }
        }
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
        loadPlaylistItems(false)
    }

    private fun loadPlaylists(refresh: Boolean) {
        if(viewModel.allLists === null || refresh) {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.allLists = RoomDB.DB_INSTANCE.playlistManager().listAllPlaylist().map {
                    PlaylistInfo(it.key, it.value.first, it.value.second)
                }.sortedWith { a, b ->
                    Utils.compareStringNaturally(a.first, b.first)
                }

                withContext(Dispatchers.Main) {
                    itemsAdapter.setNewList(viewModel.allLists!!.map {
                        ListsItem(it)
                    })

                    inSelection = false
                    updateMenu()
                }
            }
        } else {
            itemsAdapter.setNewList(viewModel.allLists!!.map {
                ListsItem(it)
            })

            inSelection = false
        }
    }

    private fun loadPlaylistItems(refresh: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            if(viewModel.playlistItems === null || refresh) {
                viewModel.playlistItems = viewModel.selectedPlaylist!!.items()
            }

            withContext(Dispatchers.Main) {
                val isStaticPl = viewModel.selectedPlaylist is StaticPlaylist
                itemsAdapter.setNewList(viewModel.playlistItems!!.map {
                    MediaFileItem(it, isStaticPl, useTitle = true, showThumb = true)
                })

                updateMenu()
            }
        }

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

        menu.add(Menu.NONE, MNU_ID_OVERVIEW_EDIT_DYN, Menu.NONE, R.string.playlist_edit_dyn).apply {
            setOnMenuItemClickListener {
                itemsAdapter.getSelectExtension().selectedItems.iterator().next().castTo<ListsItem>().playlist.third.let{ id ->
                    CoroutineScope(Dispatchers.IO).launch {
                        RoomDB.DB_INSTANCE.dynamicPlaylistDao().load(id).let {
                            withContext(Dispatchers.Main) {
                                onEditDynamicPlaylist(it)
                            }
                        }
                    }
                }

                true
            }
        }
    }
    private fun buildMenuItemsForPlaylist(menu: Menu) {
        val isStaticPlaylist = viewModel.selectedPlaylist!! is StaticPlaylist
        val isDynamicPlaylist = viewModel.selectedPlaylist!! is DynamicPlaylist

        if(isDynamicPlaylist) {
            menu.add(Menu.NONE, MNU_ID_PLAYLIST_EDIT_DYN, Menu.NONE, R.string.playlist_edit_dyn).apply {
                setOnMenuItemClickListener {
                    onEditDynamicPlaylist(viewModel.selectedPlaylist as DynamicPlaylist)
                    true
                }
            }
        }

        if(isStaticPlaylist) {
            menu.add(Menu.NONE, MNU_ID_PLAYLIST_RM, Menu.NONE, R.string.misc_remove).apply {
                setOnMenuItemClickListener {
                    onDeleteSelectedMedia()
                    true
                }
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
        val dialogOpen = mainVM.currentDialog.value != MainActivityViewModel.Dialogs.NONE

        menu.findItem(MNU_ID_OVERVIEW_RM).apply {
            isEnabled = itemsAdapter.getSelectExtension().selectedItems.isNotEmpty() && !dialogOpen
        }

        menu.findItem(MNU_ID_OVERVIEW_NEW_DYN).apply {
            isEnabled = !dialogOpen
        }

        menu.findItem(MNU_ID_OVERVIEW_EDIT_DYN).apply {
            val selection = itemsAdapter.getSelectExtension().selectedItems.toList()
            val oneDynplSelected = selection.size == 1
                    && selection[0].castTo<ListsItem>().playlist.second == PlaylistType.DYNAMIC
            isEnabled = oneDynplSelected && !dialogOpen
        }
    }
    private fun updatePlaylistMenu(menu: Menu) {
        val dialogOpen = mainVM.currentDialog.value != MainActivityViewModel.Dialogs.NONE

        menu.findItem(MNU_ID_PLAYLIST_RM)?.apply {
            val staticPlaylist = viewModel.selectedPlaylist is StaticPlaylist
            val someSelected = itemsAdapter.getSelectExtension().selectedItems.isNotEmpty()
            isEnabled = staticPlaylist && someSelected
        }

        menu.findItem(MNU_ID_PLAYLIST_INFO)?.apply {
            val mediaSelected = viewModel.selectedMedia !== null
            isEnabled = !dialogOpen && mediaSelected
        }

        menu.findItem(MNU_ID_PLAYLIST_EDIT_DYN)?.apply {
            isEnabled = !dialogOpen
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

                                withContext(Dispatchers.Main) {
                                    dlg.dismiss()
                                    onEditDynamicPlaylist(pl)
                                }
                            }catch (e: Exception){
                                Log.e(LOG_TAG, "exception while creating dynamic-playlist", e)

                                withContext(Dispatchers.Main){
                                    Toast.makeText(ctx,
                                        R.string.dynpl_edit_name_not_saved,
                                        Toast.LENGTH_LONG).show()
                                }
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
                loadPlaylistItems(true)
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

    override val type: Int = R.layout.playlists_fragment.shl(3) + 1
    override val layoutRes: Int = R.layout.playlists_entry_list

    private var thumb: Bitmap? = null

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : SelectableItem.ViewHolder<ListsItem>(view) {

        private val name = this.itemView.findViewById<TextView>(R.id.pls_lists_name)
        private val thumb = this.itemView.findViewById<ImageView>(R.id.pls_lists_thumb)

        override fun bindView(item: ListsItem, payloads: List<Any>) {
            super.bindView(item, payloads)

            name.text = item.playlist.first

            thumb.setImageDrawable(null)
            setThumbOnLayout(item)
        }
        override fun unbindView(item: ListsItem) {
            super.unbindView(item)

            name.text = null
            thumb.setImageDrawable(null)
        }

        private fun setThumbOnLayout(item: ListsItem) {
            if(thumb.width > 0 && thumb.height > 0) {
                setThumb(item)
            } else {
                thumb.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        thumb.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        setThumb(item)
                    }
                })
            }
        }

        private fun setThumb(item: ListsItem) {
            CoroutineScope(Dispatchers.IO).launch {
                item.createThumb(thumb.width, thumb.height, itemView.context).let { content ->
                    withContext(Dispatchers.Main) {
                        thumb.setImageBitmap(content)
                    }
                }
            }
        }
    }

    private suspend fun createThumb(w: Int, h: Int, ctx: Context): Bitmap {
        thumb?.let { thumb ->
            if(thumb.width == w && thumb.height == h)
                return thumb
        }

        return withContext(Dispatchers.IO) {
            val components = loadPlaylistThumbComponents(playlist)

            val thumb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(thumb)

            if(components.isEmpty()) {
                canvas.drawColor(Color.GRAY)

                val replacementThumb = ContextCompat.getDrawable(ctx, R.drawable.baseline_playlist_play_24)!!
                replacementThumb.setBounds(
                    (canvas.width * 0.05).toInt(), 0,// offset the image 5% to the right so that it looks a bit more centered
                    canvas.width, canvas.height
                )
                replacementThumb.draw(canvas)
            } else {
                canvas.drawColor(Color.TRANSPARENT)

                val divW = w / sqrt(components.size.toDouble()).toInt()
                val divH = h / sqrt(components.size.toDouble()).toInt()

                var t = 0f
                var l = 0f
                for(component in components) {
                    val scaled = Bitmap.createScaledBitmap(component, divW, divH, true)
                    canvas.drawBitmap(scaled, l, t, null)

                    scaled.recycle()
                    component.recycle()

                    l += divW
                    if(l >= thumb.width) {
                        l = 0f
                        t += divH
                    }
                }
            }

            this@ListsItem.thumb = thumb
            return@withContext thumb
        }
    }

    private fun loadPlaylistThumbComponents(pli: PlaylistInfo): List<Bitmap> {
        val pl = when(pli.second) {
            PlaylistType.STATIC -> RoomDB.DB_INSTANCE.staticPlaylistDao().load(pli.third)
            PlaylistType.DYNAMIC -> RoomDB.DB_INSTANCE.dynamicPlaylistDao().load(pli.third)
        }
        val items = pl.items()

        fun evenCount(n: Int) = if(n >= 4)
            4
        else if(n >= 1)
            1
        else
            0

        val count = evenCount(items.size)
        if(count == 0)
            return emptyList()

        val thumbs = ArrayList<Bitmap>(count)
        // try to get the thumbnail of the first <count> items
        for(i in items.indices) {
            val itm = items[i]
            val thumb = VlcManagers.getMediaDB().getSubject().getThumbnail(itm, -1, -1)
            if(thumb != null)
                thumbs.add(thumb)

            if(thumbs.size == count)
                break
        }

        if(thumbs.size < count) {
            // to many items were unsuccessful -> trim to even count
            return thumbs.take(evenCount(thumbs.size))
        }
        return thumbs
    }
}
//endregion
