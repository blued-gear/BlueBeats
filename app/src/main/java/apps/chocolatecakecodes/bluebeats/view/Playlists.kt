package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
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
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.drag.IDraggable
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.items.AbstractItem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

internal class Playlists : Fragment() {

    companion object {
        fun newInstance() = Playlists()
    }

    private var viewModel: PlaylistsViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var itemsAdapter: GenericFastItemAdapter by OnceSettable()
    private var itemsDragCallback: SimpleDragCallback by OnceSettable()
    private var itemsMoveDebounceChannel: SendChannel<Unit> by OnceSettable()
    private var titleText = RequireNotNull<TextView>()
    private var upBtn = RequireNotNull<ImageButton>()

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

        mainVM.menuProvider.value = null// don't have a menu yet so remove remaining values

        if(viewModel.showOverview.value == true)
            loadPlaylists(true)
        else
            loadPlaylistItems()
    }

    override fun onDestroyView() {
        titleText.set(null)
        upBtn.set(null)

        super.onDestroyView()
    }

    override fun onDestroy() {
        itemsMoveDebounceChannel.close()

        super.onDestroy()
    }

    private fun setupFastAdapter() {
        itemsAdapter = GenericFastItemAdapter()

        itemsDragCallback = SimpleDragCallback()
        itemsDragCallback.notifyAllDrops = false
        itemsDragCallback.isDragEnabled = false

        setupItemMoveDebounce()
        itemsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                onItemsMoved(fromPosition, toPosition, itemCount)
            }
        })
    }

    private fun setupRecycleView() {
        val recyclerView = this.requireView().findViewById<RecyclerView>(R.id.pls_entries)
        recyclerView.layoutManager = LinearLayoutManager(this.requireContext(), LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = itemsAdapter
        ItemTouchHelper(itemsDragCallback).attachToRecyclerView(recyclerView)
    }

    //region action handlers
    private fun wireActionHandlers() {
        wireItemsClickListener()
        wireUpBtn()
    }

    private fun wireItemsClickListener(){
        itemsAdapter.fastAdapter!!.onClickListener = { _, _, item, _ ->
            if(viewModel.selectedPlaylist === null) {
                onSelectPlaylist((item as ListsItem).playlist)
            } else {
                onPlayPlaylistAt((item as MediaItem).media)
            }

            false
        }
    }
    private fun onSelectPlaylist(playlistInfo: PlaylistInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.selectedPlaylist = when (playlistInfo.second) {
                PlaylistType.STATIC -> RoomDB.DB_INSTANCE.staticPlaylistDao()
                    .load(playlistInfo.third)
                PlaylistType.DYNAMIC -> TODO()
            }

            viewModel.showOverview.postValue(false)
        }
    }
    private fun onPlayPlaylistAt(media: MediaFile) {
        TODO()
    }

    @OptIn(FlowPreview::class)
    private fun setupItemMoveDebounce() {
        CoroutineScope(Dispatchers.Default).launch {
            callbackFlow<Unit> {
                itemsMoveDebounceChannel = channel
                awaitClose()
            }.debounce(2000).collect {
                (viewModel.selectedPlaylist as? StaticPlaylist)?.let {
                    withContext(Dispatchers.IO) {
                        RoomDB.DB_INSTANCE.staticPlaylistDao().save(it)
                    }
                }
            }
        }
    }

    private fun onItemsMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        val playlist = viewModel.selectedPlaylist!! as StaticPlaylist// only supported static playlist

        CoroutineScope(Dispatchers.IO).launch {
            (0 until itemCount).map {
                val from = fromPosition + it
                val to = toPosition + it
                playlist.moveMedia(from, to)
            }

            itemsMoveDebounceChannel.send(Unit)
        }
    }

    private fun wireUpBtn() {
        upBtn.get().setOnClickListener {
            viewModel.showOverview.postValue(true)
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
        }
    }

    private fun showPlaylists() {
        if(viewModel.allLists !== null)// already showing playlists
            return

        viewModel.playlistItems = null
        viewModel.selectedPlaylist = null
        loadPlaylists(false)

        titleText.get().text = null
        upBtn.get().visibility = Button.INVISIBLE

        itemsDragCallback.isDragEnabled = false
    }

    private fun showPlaylistItems() {
        if(viewModel.playlistItems !== null)// already showing playlist-items
            return

        viewModel.allLists = null
        loadPlaylistItems()

        titleText.get().text = viewModel.selectedPlaylist!!.name
        upBtn.get().visibility = Button.VISIBLE

        itemsDragCallback.isDragEnabled = viewModel.selectedPlaylist is StaticPlaylist
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
                }
            }
        } else {
            itemsAdapter.setNewList(viewModel.allLists!!.map {
                ListsItem(it)
            })
        }
    }

    private fun loadPlaylistItems() {
        if(viewModel.playlistItems === null) {
            viewModel.playlistItems = viewModel.selectedPlaylist!!.items()
        }

        itemsAdapter.setNewList(viewModel.playlistItems!!.map {
            MediaItem(it)
        })
    }
}

//region RecycleView-Items
private class ListsItem(val playlist: PlaylistInfo) : AbstractItem<ListsItem.ViewHolder>() {

    override val type: Int = R.layout.playlists_fragment * 100 + 1
    override val layoutRes: Int = R.layout.playlists_entry_list

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : FastAdapter.ViewHolder<ListsItem>(view) {

        private val name = this.itemView.findViewById<TextView>(R.id.pls_lists_name)

        override fun bindView(item: ListsItem, payloads: List<Any>) {
            name.text = item.playlist.first
        }
        override fun unbindView(item: ListsItem) {
            name.text = null
        }
    }
}

private class MediaItem(val media: MediaFile) : AbstractItem<MediaItem.ViewHolder>(), IDraggable {

    override val type: Int = R.layout.playlists_fragment * 100 + 2
    override val layoutRes: Int = R.layout.view_media_node

    override val isDraggable: Boolean = true

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : FastAdapter.ViewHolder<MediaItem>(view) {

        private val title: TextView = view.findViewById(R.id.v_mn_text)

        override fun bindView(item: MediaItem, payloads: List<Any>) {
            title.text = item.media.name
        }
        override fun unbindView(item: MediaItem) {
            title.text = null
        }
    }
}
//endregion
