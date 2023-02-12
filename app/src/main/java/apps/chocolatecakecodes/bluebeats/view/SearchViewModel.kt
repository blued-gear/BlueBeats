@file:Suppress("NestedLambdaShadowedImplicitParameter")

package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce

private const val SEARCH_DEBOUNCE_TIMEOUT = 200L

@OptIn(FlowPreview::class)
internal class SearchViewModel : ViewModel() {

    enum class Grouping { FILENAME, TITLE, ID3_TAG, USER_TAG }

    private val groupingRW = MutableLiveData<Grouping>()
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
            CoroutineScope(Dispatchers.IO).launch {
                loadItems(it, subgroup)
            }
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
                RoomDB.DB_INSTANCE.id3TagDao().getAllTagTypes().filterNot {
                    it == "length"
                        || it == "title"
                }.let {
                    subgroupsRW.postValue(it)
                    setSubgroup(if(it.isNotEmpty()) it.first() else null)
                }
            }
            else -> throw AssertionError()
        }
    }

    private fun loadItems(grouping: Grouping, subgroup: String?) {
        when(grouping) {
            Grouping.FILENAME -> loadByFilename()
            Grouping.TITLE -> loadByTitle()
            Grouping.ID3_TAG -> loadByID3Tag(subgroup!!)
            Grouping.USER_TAG -> loadByUsertag()
        }
    }

    private fun loadByFilename() {
        allItems = RoomDB.DB_INSTANCE.mediaFileDao().getAllFiles().groupBy {
            textGroupingChar(it.name)
        }.mapValues {
            it.value.sortedBy {
                it.name
            }
        }.toSortedMap()

        loadLazyFileAttributes(allItems.values)

        itemsRW.postValue(allItems)
    }

    private fun loadByTitle() {
        allItems = RoomDB.DB_INSTANCE.id3TagDao().getFilesWithAnyTag("title").groupBy {
            textGroupingChar(it.second)
        }.mapValues {
            it.value.sortedBy {
                it.second
            }.map {
                it.first
            }
        }.toSortedMap()

        loadLazyFileAttributes(allItems.values)

        itemsRW.postValue(allItems)
    }

    private fun loadByID3Tag(type: String) {
        allItems = RoomDB.DB_INSTANCE.id3TagDao().getFilesWithAnyTag(type).groupBy {
            it.second
        }.mapValues {
            it.value.sortedBy {
                it.second
            }.map {
                it.first
            }
        }.toSortedMap()

        loadLazyFileAttributes(allItems.values)

        itemsRW.postValue(allItems)
    }

    private fun loadByUsertag() {
        allItems = RoomDB.DB_INSTANCE.userTagDao().let { dao ->
            dao.getAllUserTags().associateWith {
                dao.getFilesForTags(listOf(it)).map {
                    it.key
                }.sortedBy {
                    it.name
                }
            }
        }.toSortedMap()

        loadLazyFileAttributes(allItems.values)

        itemsRW.postValue(allItems)
    }

    private fun search(query: String) {
        ;
    }

    private fun setupSearchDebouncer() {
        CoroutineScope(Dispatchers.IO).launch {
            callbackFlow {
                this@SearchViewModel.addCloseable {
                    this.close()
                }

                debounceSearch = {
                    trySend(it)
                }

                awaitClose {  }
            }.debounce(SEARCH_DEBOUNCE_TIMEOUT).collect {
                withContext(Dispatchers.IO) {
                    search(it)
                }
            }
        }
    }

    private fun hasSubgroup(grouping: Grouping) = when(grouping) {
        Grouping.ID3_TAG -> true
        else -> false
    }

    private fun textGroupingChar(str: String) = when(val c = str.first()) {
        in '0'..'9' -> "0-9"
        in 'a'..'z', in 'A'..'Z' -> c.uppercase()
        in listOf('ä', 'Ä', 'ü', 'Ü', 'ö', 'Ö', 'ß') -> "Ä"
        else -> "#"
    }

    @Suppress("UnusedEquals")
    private fun loadLazyFileAttributes(items: Collection<List<MediaFile>>) {
        items.flatten().forEach {
            it.mediaTags.equals(null)
        }
    }
}
