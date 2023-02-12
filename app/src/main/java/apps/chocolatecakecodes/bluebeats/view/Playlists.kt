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
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class Playlists : Fragment() {

    companion object {
        fun newInstance() = Playlists()
    }

    private var viewModel: PlaylistsViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var itemsAdapter: GenericItemAdapter by OnceSettable()
    private var titleText: TextView by OnceSettable()
    private var upBtn: ImageButton by OnceSettable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlaylistsViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.playlists_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleText = view.findViewById(R.id.pls_title)
        upBtn = view.findViewById(R.id.pls_up_btn)

        setupRecycleView()
        wireActionHandlers()
        wireObservers()
    }

    override fun onResume() {
        super.onResume()

        mainVM.menuProvider.value = null// don't have a menu yet so remove remaining values
    }

    private fun setupRecycleView() {
        itemsAdapter = GenericItemAdapter()
        val fastAdapter = FastAdapter.with(itemsAdapter)
        this.requireView().findViewById<RecyclerView>(R.id.pls_entries).adapter = fastAdapter
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
        viewModel.selectedPlaylist = when(playlistInfo.second) {
            PlaylistType.STATIC -> RoomDB.DB_INSTANCE.staticPlaylistDao().load(playlistInfo.third)
            PlaylistType.DYNAMIC -> TODO()
        }

        viewModel.showOverview.postValue(false)
    }
    private fun onPlayPlaylistAt(media: MediaFile) {
        TODO()
    }

    private fun wireUpBtn() {
        upBtn.setOnClickListener {
            viewModel.showOverview.postValue(false)
        }
    }
    //endregion

    private fun wireObservers() {
        viewModel.showOverview.observe(this.viewLifecycleOwner) {
            if(it) {
                viewModel.playlistItems = null
                loadPlaylists()

                titleText.text = null
                upBtn.visibility = Button.INVISIBLE
            } else {
                viewModel.allLists = null
                loadPlaylistItems()

                titleText.text = viewModel.selectedPlaylist!!.name
                upBtn.visibility = Button.VISIBLE
            }
        }
    }

    private fun loadPlaylists() {
        if(viewModel.allLists === null) {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.allLists = RoomDB.DB_INSTANCE.playlistManager().listAllPlaylist().map {
                    PlaylistInfo(it.key, it.value.first, it.value.second)
                }

                withContext(Dispatchers.Main) {
                    itemsAdapter.set(viewModel.allLists!!.map {
                        ListsItem(it)
                    })
                }
            }
        } else {
            itemsAdapter.set(viewModel.allLists!!.map {
                ListsItem(it)
            })
        }
    }

    private fun loadPlaylistItems() {
        if(viewModel.playlistItems === null) {
            viewModel.playlistItems = viewModel.selectedPlaylist!!.items()
        }

        itemsAdapter.set(viewModel.playlistItems!!.map {
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

private class MediaItem(val media: MediaFile) : AbstractItem<MediaItem.ViewHolder>() {

    override val type: Int = R.layout.playlists_fragment * 100 + 2
    override val layoutRes: Int = R.layout.view_media_node

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
