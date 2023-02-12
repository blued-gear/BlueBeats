@file:Suppress("NestedLambdaShadowedImplicitParameter")

package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

private const val SEARCH_DEBOUNCE_TIMEOUT = 200L

@OptIn(FlowPreview::class)
internal class SearchViewModel : ViewModel() {

    enum class Grouping { FILENAME, TITLE, TYPE, ID3_TAG, USER_TAG }

    private val groupingRW = MutableLiveData<Grouping>()
    val grouping: LiveData<Grouping> = groupingRW
    private val subgroupsRW = MutableLiveData<List<String>>()
    val subgroups: LiveData<List<String>> = subgroupsRW
    private val itemsRW = MutableLiveData<Map<String, List<MediaFile>>>()
    val items: LiveData<Map<String, List<MediaFile>>> = itemsRW
    private val subgroupRW = MutableLiveData<String?>()
    val subgroup: LiveData<String?> = subgroupRW
    private val searchTextRW = MutableLiveData("")
    val searchText: LiveData<String> = searchTextRW

    lateinit var contextProvider: () -> Context

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
            Grouping.TYPE -> loadByType()
            Grouping.ID3_TAG -> loadByID3Tag(subgroup!!)
            Grouping.USER_TAG -> loadByUsertag()
        }

        searchText.value?.let {
            search(it)
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
    }

    private fun loadByType() {
        allItems = RoomDB.DB_INSTANCE.mediaFileDao().getAllFiles().groupBy {
            fileTypeStr(it.type, contextProvider())
        }.mapValues {
            it.value.sortedBy {
                it.title
            }
        }.toSortedMap()

        loadLazyFileAttributes(allItems.values)
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
    }

    private fun search(query: String) {
        grouping.value?.let {
            if(query.isEmpty()) {
                if (allItems != items.value)
                    itemsRW.postValue(allItems)
            } else {
                val parsedQuery = parseSearchQuery(query)
                when (it) {
                    Grouping.FILENAME -> {
                        allItems.flatMap { group ->
                            group.value.map {
                                Pair(it.name, Pair(group.key, it))
                            }
                        }.let {
                            performSearch(parsedQuery, it)
                        }.groupBy {
                            it.first
                        }.mapValues {
                            it.value.map { it.second }
                        }
                    }
                    Grouping.TITLE -> {
                        allItems.flatMap { group ->
                            group.value.map {
                                Pair(it.mediaTags.title, Pair(group.key, it))
                            }
                        }.let {
                            performSearch(parsedQuery, it)
                        }.groupBy {
                            it.first
                        }.mapValues {
                            it.value.map { it.second }
                        }
                    }
                    Grouping.TYPE -> {
                        allItems.flatMap { group ->
                            group.value.map {
                                Pair(it.title, Pair(group.key, it))
                            }
                        }.let {
                            performSearch(parsedQuery, it)
                        }.groupBy {
                            it.first
                        }.mapValues {
                            it.value.map { it.second }
                        }
                    }
                    Grouping.ID3_TAG, Grouping.USER_TAG -> {
                        allItems.map {
                            Pair(it.key, Pair(it.key, it.value))
                        }.let {
                            performSearch(parsedQuery, it)
                        }.groupBy {
                            it.first
                        }.mapValues {
                            it.value.flatMap { it.second }
                        }
                    }
                }.let {
                    if (it != items.value)
                        itemsRW.postValue(it)
                }
            }
        }
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

    private fun fileTypeStr(type: MediaFile.Type, context: Context) = when(type) {
        MediaFile.Type.AUDIO -> context.getString(R.string.misc_filetype_audio)
        MediaFile.Type.VIDEO -> context.getString(R.string.misc_filetype_video)
        MediaFile.Type.OTHER -> context.getString(R.string.misc_filetype_other)
    }

    @Suppress("UnusedEquals")
    private fun loadLazyFileAttributes(items: Collection<List<MediaFile>>) {
        items.flatten().forEach {
            it.mediaTags.equals(null)
        }
    }

    /**
     * Parses the query-string to a list of matchers.
     * Matchers is a list of lists of strings; the inner list represent AND combinations,
     *  the outer OR combinations of the results of the ANDs.
     * How the string is split: <and-1-1>,<and-1-2>,<and-1-3>;<and-2-1>,<and-2-2>
     */
    private fun parseSearchQuery(query: String): List<List<String>> {
        return query.split(';').map {
            it.split(',').map {
                it.removePrefix(" ")
            }.filterNot {
                it.isEmpty() || it.isBlank()
            }
        }.filterNot {
            it.isEmpty()
        }
    }

    /**
     * Applies the matchers against every string from items (item.first)
     *  and returns the items (item.second) where all AND-substrings of at least one OR-list matched.
     */
    private fun <R> performSearch(matchers: List<List<String>>, items: List<Pair<String, R>>): List<R> {
        return items.filter { item ->
            matchers.any {
                it.all {
                    item.first.contains(it, true)
                }
            }
        }.map {
            it.second
        }
    }
}
