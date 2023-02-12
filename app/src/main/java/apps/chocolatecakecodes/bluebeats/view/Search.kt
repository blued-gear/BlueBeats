@file:Suppress("NestedLambdaShadowedImplicitParameter")

package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import com.google.android.material.tabs.TabLayout
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem

internal class Search : Fragment(R.layout.search_fragment) {

    companion object {
        fun newInstance() = Search()
    }

    private var viewModel: SearchViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()

    private var subgroupsAdapter: ArrayAdapter<String> by OnceSettable()
    private var itemListAdapter: FastItemAdapter<GroupItem> by OnceSettable()

    private val tabs = RequireNotNull<TabLayout>()
    private val searchText = RequireNotNull<EditText>()
    private val subgroupsSpinner = RequireNotNull<Spinner>()
    private val itemListView = RequireNotNull<RecyclerView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(SearchViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)
        playerVM = vmProvider.get(PlayerViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabs.set(view.findViewById(R.id.search_tabs))
        searchText.set(view.findViewById(R.id.search_search_text))
        subgroupsSpinner.set(view.findViewById(R.id.search_subgroups))
        itemListView.set(view.findViewById(R.id.search_items))

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
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        tabs.set(null)
        searchText.set(null)
        subgroupsSpinner.set(null)
        itemListView.set(null)
    }

    private fun setupSubgroupsSpinner() {
        subgroupsAdapter = ArrayAdapter(this.requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
        subgroupsSpinner.get().adapter = subgroupsAdapter
    }

    private fun setupItemList() {
        itemListAdapter = FastItemAdapter()
        itemListAdapter.getExpandableExtension().apply {
            isOnlyOneExpandedItem = false
        }

        itemListView.get().apply {
            layoutManager = LinearLayoutManager(this@Search.requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = itemListAdapter
        }
    }

    //region action handlers
    private fun wireActionHandlers() {
        setupTabSelectionListener()
        setupSubgroupsSelectionListener()

        searchText.get().doAfterTextChanged {
            viewModel.setSearchText(it.toString())
        }
    }

    private fun setupTabSelectionListener() {
        tabs.get().addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when(tab.position){
                    0 -> SearchViewModel.Grouping.FILENAME
                    1 -> SearchViewModel.Grouping.TITLE
                    2 -> SearchViewModel.Grouping.ID3_TAG
                    3 -> SearchViewModel.Grouping.USER_TAG
                    else -> throw AssertionError()
                }.let {
                    viewModel.setGrouping(it)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                //TODO maybe jump to top
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
                it.map { group ->
                    group.value.map {
                        MediaFileSubItem(it)
                    }.let {
                        GroupItem(group.key, it)
                    }
                }.let {
                    itemListAdapter.set(it)
                }
            }
        }
    }

    private fun applyTabSelection(grouping: SearchViewModel.Grouping) {
        val tabs = tabs.get()
        when(grouping) {
            SearchViewModel.Grouping.FILENAME -> tabs.selectTab(tabs.getTabAt(0))
            SearchViewModel.Grouping.TITLE -> tabs.selectTab(tabs.getTabAt(1))
            SearchViewModel.Grouping.ID3_TAG -> tabs.selectTab(tabs.getTabAt(2))
            SearchViewModel.Grouping.USER_TAG -> tabs.selectTab(tabs.getTabAt(3))
        }

        // show / hide subgroups
        subgroupsSpinner.get().visibility = when(grouping) {
            SearchViewModel.Grouping.ID3_TAG ->  View.VISIBLE
            else -> View.GONE
        }
    }

    private fun applySearchText(text: String) {
        searchText.get().let {
            it.text.clear()
            it.text.append(text)
        }
    }
    //endregion
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
    draggable: Boolean = false
) : MediaFileItem(file, draggable), ISubItem<MediaFileItem.ViewHolder> {
    override var parent: IParentItem<*>? = null
}
//endregion
