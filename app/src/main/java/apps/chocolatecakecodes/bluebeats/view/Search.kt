@file:Suppress("NestedLambdaShadowedImplicitParameter")

package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.media.playlist.TempPlaylist
import apps.chocolatecakecodes.bluebeats.util.*
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import com.google.android.material.tabs.TabLayout
import com.mikepenz.fastadapter.*
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.*

internal class Search : Fragment(R.layout.search_fragment) {

    companion object {
        fun newInstance() = Search()
    }

    private var viewModel: SearchViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()

    private var subgroupsAdapter: ArrayAdapter<String> by OnceSettable()
    private var itemListAdapter: FastItemAdapter<GroupItem> by OnceSettable()

    private val tabs = RequireNotNull<TabLayout>()
    private val searchText = RequireNotNull<EditText>()
    private val subgroupsSpinner = RequireNotNull<Spinner>()
    private val itemListView = RequireNotNull<RecyclerView>()
    private val itemListRefresh = RequireNotNull<SwipeRefreshLayout>()

    private var menu: Menu? = null
    private var inSelection = false

    private val player: VlcPlayer
        get() = requireActivity().castTo<MainActivity>().playerConn.player!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(SearchViewModel::class.java).apply {
            contextProvider = { this@Search.requireContext() }
        }
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabs.set(view.findViewById(R.id.search_tabs))
        searchText.set(view.findViewById(R.id.search_search_text))
        subgroupsSpinner.set(view.findViewById(R.id.search_subgroups))
        itemListView.set(view.findViewById(R.id.search_items))
        itemListRefresh.set(view.findViewById(R.id.search_items_sr))

        wireActionHandlers()
        wireObservers()

        setupSubgroupsSpinner()
        setupItemList()
    }

    override fun onStart() {
        super.onStart()

        if(viewModel.grouping.value == null)
            viewModel.setGrouping(SearchViewModel.Grouping.FILENAME)
    }

    override fun onResume() {
        super.onResume()

        setupMenu()
    }

    override fun onPause() {
        super.onPause()

        menu = null
    }

    override fun onDestroyView() {
        super.onDestroyView()

        tabs.set(null)
        searchText.set(null)
        subgroupsSpinner.set(null)
        itemListView.set(null)
    }

    override fun onDestroy() {
        viewModel.contextProvider = null

        super.onDestroy()
    }

    private fun setupSubgroupsSpinner() {
        subgroupsAdapter = ArrayAdapter(this.requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
        subgroupsSpinner.get().adapter = subgroupsAdapter
    }

    private fun setupItemList() {
        itemListAdapter = FastItemAdapter<GroupItem>().apply {
            setHasStableIds(true)

            // prevent click-event when long-click for selection
            onLongClickListener = { _, _, _: GenericItem, _ ->
                true
            }

            onClickListener =  { _, _, item: GenericItem, pos ->
                if(item is MediaFileSubItem)
                    onItemClick(item, pos)
                true
            }
        }
        itemListAdapter.getExpandableExtension().apply {
            isOnlyOneExpandedItem = false
        }
        itemListAdapter.getSelectExtension().apply {
            isSelectable = true
            multiSelect = true
            allowDeselection = true
            selectOnLongClick = true

            @Suppress("UNCHECKED_CAST")
            selectionListener = (object : ISelectionListener<GenericItem> {
                override fun onSelectionChanged(item: GenericItem, selected: Boolean) {
                    if(item is MediaFileItem)
                        this@Search.onSelectionChanged()
                }
            }) as ISelectionListener<GroupItem>
        }

        itemListView.get().apply {
            layoutManager = LinearLayoutManager(this@Search.requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = itemListAdapter
        }
    }

    //region menu
    private fun setupMenu() {
        mainVM.menuProvider.value = { menu, menuInflater ->
            menuInflater.inflate(R.menu.search_menu, menu)

            this.menu = menu

            menu.findItem(R.id.search_mnu_exp).setOnMenuItemClickListener {
                itemListAdapter.getExpandableExtension().expand()
                true
            }
            menu.findItem(R.id.search_mnu_colp).setOnMenuItemClickListener {
                itemListAdapter.getExpandableExtension().collapse()
                true
            }
            menu.findItem(R.id.search_mnu_addpl).apply {
                setOnMenuItemClickListener {
                    onAddToPlaylist()
                    true
                }
            }
            menu.findItem(R.id.search_mnu_start_tpl).apply {
                setOnMenuItemClickListener {
                    onStartTmpPlClicked()
                    true
                }
            }
            menu.findItem(R.id.search_mnu_fi).apply {
                setOnMenuItemClickListener {
                    onShowFileInfo()
                    true
                }
            }
            menu.findItem(R.id.search_mnu_sela).apply {
                setOnMenuItemClickListener {
                    onSelectAll()
                    true
                }
            }

            updateMenu()
        }
    }
    //endregion

    //region action handlers
    private fun wireActionHandlers() {
        setupTabSelectionListener()
        setupSubgroupsSelectionListener()

        searchText.get().doAfterTextChanged {
            viewModel.setSearchText(searchText.get().text.toString())
        }

        itemListRefresh.get().setOnRefreshListener {
            viewModel.refresh()

            // show the animation just for a moment
            CoroutineScope(Dispatchers.Default).launch {
                delay(200)
                withContext(Dispatchers.Main) {
                    itemListRefresh.get().isRefreshing = false
                }
            }
        }


        this.requireActivity().onBackPressedDispatcher.addCallback(SmartBackPressedCallback(this.lifecycle, this::onBackPressed))
    }

    private fun setupTabSelectionListener() {
        tabs.get().addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when(tab.position){
                    0 -> SearchViewModel.Grouping.FILENAME
                    1 -> SearchViewModel.Grouping.TITLE
                    2 -> SearchViewModel.Grouping.TYPE
                    3 -> SearchViewModel.Grouping.ID3_TAG
                    4 -> SearchViewModel.Grouping.USER_TAG
                    else -> throw AssertionError()
                }.let {
                    viewModel.setGrouping(it)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if(itemListAdapter.adapterItems.isNotEmpty())
                    itemListView.get().scrollToPosition(0)
            }
        })
    }

    private fun setupSubgroupsSelectionListener() {
        subgroupsSpinner.get().onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                viewModel.setSubgroup(subgroupsAdapter.getItem(position)!!)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun onBackPressed() {
        if(mainVM.currentDialog.value == MainActivityViewModel.Dialogs.FILE_DETAILS) {// close dialog
            this.parentFragmentManager.beginTransaction()
                .remove(this.parentFragmentManager.findFragmentByTag(MainActivityViewModel.Dialogs.FILE_DETAILS.tag)!!)
                .commit()

            Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.NONE)
            updateMenu()
        } else {// clear selection
            itemListAdapter.getSelectExtension().deselect()
        }
    }

    private fun onItemClick(item: MediaFileItem, pos: Int) {
        if (inSelection){
            itemListAdapter.getSelectExtension().toggleSelection(pos)
        } else {
            mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
            player.playMedia(item.file)
        }
    }

    private fun onSelectionChanged() {
        updateMenu()
        inSelection = itemListAdapter.getSelectExtension().selectedItems.size > 0
    }

    private fun onAddToPlaylist() {
        val toAdd = itemListAdapter.getSelectExtension().selectedItems
            .filterIsInstance<MediaFileItem>().map {
                it.file
            }.sortedBy {
                it.name
            }
        showAddToPlDlg(this.requireContext(), toAdd)
    }

    private fun onStartTmpPlClicked() {
        val toAdd = itemListAdapter.getSelectExtension().selectedItems
            .filterIsInstance<MediaFileItem>().map {
                it.file
            }.sortedBy {
                it.name
            }

        val pl = player.getCurrentPlaylist()
        if(pl is TempPlaylist) {
            pl.addMedias(toAdd)
        } else {
            TempPlaylist().also { tpl ->
                tpl.addMedias(toAdd)
            }.let {
                player.playPlaylist(it)
            }

            mainVM.currentTab.postValue(MainActivityViewModel.Tabs.PLAYER)
        }
    }

    private fun onShowFileInfo() {
        Utils.trySetValueImmediately(mainVM.currentDialog, MainActivityViewModel.Dialogs.FILE_DETAILS)

        val dlg = FileDetails(itemListAdapter.getSelectExtension().selectedItems.first().castTo<MediaFileItem>().file)
        this.parentFragmentManager.beginTransaction()
            .add(R.id.main_content, dlg, MainActivityViewModel.Dialogs.FILE_DETAILS.tag)
            .commit()

        menu?.let {
            it.findItem(R.id.search_mnu_addpl).isEnabled = false
            it.findItem(R.id.search_mnu_fi).isEnabled = false
        }
    }

    private fun onSelectAll() {
        // select all files in all expanded groups
        itemListAdapter.adapterItems
        .filterIsInstance<GroupItem>()// necessary because adapterItems also returns items of groups
        .filter {
            it.isExpanded
        }.forEach {
            it.subItems.forEach {
                val pos = itemListAdapter.getAdapterPosition(it.identifier)
                itemListAdapter.getSelectExtension().select(pos, false, true)
            }
        }
    }
    //endregion

    //region vm handlers
    private fun wireObservers() {
        viewModel.grouping.observe(this.viewLifecycleOwner) {
            it?.let {
                applyTabSelection(it)
            }
        }

        viewModel.searchText.observe(this.viewLifecycleOwner) {
            it?.let {
                applySearchText(it)
            }
        }

        viewModel.subgroups.observe(this.viewLifecycleOwner) {
            it?.let {
                subgroupsAdapter.setNotifyOnChange(false)
                subgroupsAdapter.clear()
                subgroupsAdapter.addAll(it)
                subgroupsAdapter.notifyDataSetChanged()
            }
        }
        viewModel.subgroup.observe(this.viewLifecycleOwner) {
            it?.let {
                subgroupsSpinner.get().setSelection(subgroupsAdapter.getPosition(it))
            }
        }

        viewModel.items.observe(this.viewLifecycleOwner) {
            it?.let {
                applyItems(it)
            } ?: itemListAdapter.clear()

            updateMenu()
        }
    }

    private fun applyTabSelection(grouping: SearchViewModel.Grouping) {
        val tabs = tabs.get()
        when(grouping) {
            SearchViewModel.Grouping.FILENAME -> tabs.selectTab(tabs.getTabAt(0))
            SearchViewModel.Grouping.TITLE -> tabs.selectTab(tabs.getTabAt(1))
            SearchViewModel.Grouping.TYPE -> tabs.selectTab(tabs.getTabAt(2))
            SearchViewModel.Grouping.ID3_TAG -> tabs.selectTab(tabs.getTabAt(3))
            SearchViewModel.Grouping.USER_TAG -> tabs.selectTab(tabs.getTabAt(4))
        }

        // show / hide subgroups
        subgroupsSpinner.get().visibility = when(grouping) {
            SearchViewModel.Grouping.ID3_TAG ->  View.VISIBLE
            else -> View.GONE
        }
    }

    private fun applySearchText(text: String) {
        searchText.get().let {
            if(text != it.text.toString()) {
                it.text.clear()
                it.text.append(text)
            }
        }
    }

    private fun applyItems(items: Map<String, List<MediaFile>>) {
        val useTitle = viewModel.grouping.value != SearchViewModel.Grouping.FILENAME
        items.map { group ->
            group.value.map {
                MediaFileSubItem(it, useTitle)
            }.let {
                GroupItem(group.key, it)
            }
        }.let {
            itemListAdapter.set(it)
        }

        itemListAdapter.getExpandableExtension().expand()
    }
    //endregion

    private fun updateMenu() {
        menu?.let {
            val selectedCount = itemListAdapter.getSelectExtension().selectedItems.size

            it.findItem(R.id.search_mnu_addpl).isEnabled = selectedCount > 0
            it.findItem(R.id.search_mnu_fi).isEnabled = selectedCount == 1

            val startTemPlItem = it.findItem(R.id.search_mnu_start_tpl)
            val currentPl = requireActivity().castTo<MainActivity>().playerConn.player?.getCurrentPlaylist()
            startTemPlItem.isEnabled = selectedCount > 0
            if(currentPl is TempPlaylist)
                startTemPlItem.title = this.requireContext().getText(R.string.filebrowser_menu_add_tmp_pl)
            else
                startTemPlItem.title = this.requireContext().getText(R.string.filebrowser_menu_start_tmp_pl)
        }
    }
}

//region item-models
private class GroupItem(
    val title: String,
    items: List<MediaFileSubItem>
) : AbstractExpandableItem<GroupItem.ViewHolder>() {

    override val type: Int = GroupItem::class.hashCode()
    override val layoutRes: Int = -1

    init {
        items.forEach {
            it.parent = this
        }
        this.subItems.addAll(items)
    }

    override fun createView(ctx: Context, parent: ViewGroup?): View {
        return TextView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun getViewHolder(v: View) = ViewHolder(v)

    class ViewHolder(view: View) : FastAdapter.ViewHolder<GroupItem>(view) {

        private val content: TextView = view as TextView

        override fun bindView(item: GroupItem, payloads: List<Any>) {
            content.text = item.title
        }

        override fun unbindView(item: GroupItem) {
            content.text = ""
        }
    }
}

private class MediaFileSubItem(
    file: MediaFile,
    useTitle: Boolean
) : MediaFileItem(file, false, useTitle, true), ISubItem<MediaFileItem.ViewHolder> {
    override var parent: IParentItem<*>? = null
}
//endregion
