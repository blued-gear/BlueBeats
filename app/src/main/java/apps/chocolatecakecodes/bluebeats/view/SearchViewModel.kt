package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_TIMEOUT = 200L

@OptIn(FlowPreview::class)
internal class SearchViewModel : ViewModel() {

    enum class Grouping { FILENAME, TITLE, ID3_TAG, USER_TAG }

    private val groupingRW = MutableLiveData(Grouping.FILENAME)
    val grouping: LiveData<Grouping> = groupingRW
    private val subgroupsRW = MutableLiveData<List<String>>()
    val subgroups: LiveData<List<String>> = subgroupsRW
    private val itemsRW = MutableLiveData<Map<String, List<MediaFile>>>()
    val items: LiveData<Map<String, List<MediaFile>>> = itemsRW
    private val subgroupRW = MutableLiveData<String?>()
    val subgroup: LiveData<String?> = subgroupRW
    private val searchTextRW = MutableLiveData<String>()
    val searchText: LiveData<String> = searchTextRW

    private lateinit var debounceSearch: (String) -> Unit
    private lateinit var allItems: Map<String, List<MediaFile>>

    init {
        setupSearchDebouncer()
    }

    fun setGrouping(grouping: Grouping) {
        if(grouping == groupingRW.value)
            return

        Utils.trySetValueImmediately(groupingRW, grouping)

        CoroutineScope(Dispatchers.IO).launch {
            if(hasSubgroup(grouping)){
                loadSubgroups(grouping)
            } else {
                subgroupsRW.postValue(emptyList())
                subgroupRW.postValue(null)
                loadItems(grouping, null)
            }
        }
    }

    fun setSubgroup(subgroup: String?) {
        if(subgroup == subgroupRW.value)
            return

        Utils.trySetValueImmediately(subgroupRW, subgroup)

        grouping.value?.let {
            loadItems(it, subgroup)
        }
    }

    fun setSearchText(text: String) {
        if(text == searchText.value)
            return

        Utils.trySetValueImmediately(searchTextRW, text)

        debounceSearch(text)
    }

    private fun loadSubgroups(grouping: Grouping) {
        when(grouping) {
            Grouping.ID3_TAG -> {
                RoomDB.DB_INSTANCE.id3TagDao().getAllTagTypes().let {
                    subgroupsRW.postValue(it)
                    setSubgroup(if(it.isNotEmpty()) it.first() else null)
                }
            }
            else -> throw AssertionError()
        }
    }

    private fun loadItems(grouping: Grouping, subgroup: String?) {

    }

    private fun search(query: String) {

    }

    private fun setupSearchDebouncer() {
        CoroutineScope(Dispatchers.IO).launch {
            channelFlow {
                this@SearchViewModel.addCloseable {
                    this.close()
                }

                debounceSearch = {
                    trySend(it)
                }
            }.debounce(SEARCH_DEBOUNCE_TIMEOUT).collect {
                search(it)
            }
        }
    }

    private fun hasSubgroup(grouping: Grouping) = when(grouping) {
        Grouping.ID3_TAG -> true
        else -> false
    }
}
