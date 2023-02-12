package apps.chocolatecakecodes.bluebeats.view

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import com.google.android.material.tabs.TabLayout

internal class Search : Fragment(R.layout.search_fragment) {

    companion object {
        fun newInstance() = Search()
    }

    private var viewModel: SearchViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var playerVM: PlayerViewModel by OnceSettable()

    private val tabs = RequireNotNull<TabLayout>()
    private val searchText = RequireNotNull<EditText>()
    private val subgroupsSpinner = RequireNotNull<Spinner>()
    private val itemView = RequireNotNull<RecyclerView>()

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
        itemView.set(view.findViewById(R.id.search_items))

        wireActionHandlers()
        wireObservers()
    }

    override fun onStart() {
        super.onStart()
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

    private fun wireActionHandlers() {
        tabs.get().addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.grouping.value = when(tab.position){
                    0 -> SearchViewModel.Grouping.FILENAME
                    1 -> SearchViewModel.Grouping.TITLE
                    2 -> SearchViewModel.Grouping.ID3_TAG
                    3 -> SearchViewModel.Grouping.USER_TAG
                    else -> throw AssertionError()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                //TODO maybe jump to top
            }
        })
    }

    private fun wireObservers() {
        viewModel.grouping.observe(this.viewLifecycleOwner) {
            it?.let {
                applyTabSelection(it)
                loadItems(it)
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

    private fun loadItems(grouping: SearchViewModel.Grouping) {
        //TODO
    }
}
