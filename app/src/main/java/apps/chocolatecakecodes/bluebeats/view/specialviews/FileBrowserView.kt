package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaDirItem
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.GenericFastItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun mediaNodeToItem(node: MediaNode): AbstractItem<*>? = when(node) {
    is MediaDir -> MediaDirItem(node)
    is MediaFile -> MediaFileItem(node)
    else -> null
}

internal class FileBrowserView(context: Context): FrameLayout(context){

    var itemClickListener: ((MediaNode) -> Unit)? = null
    var itemSelectionChangedListener: ((MediaNode, Boolean) -> Unit)? = null
    /** if true then a click in an item during selection will call the itemClickListener */
    var notifyClickOnSelection: Boolean = false
    /** if true then a click on a dir-item will call the itemClickListener */
    var notifyClickOnDir: Boolean = false
    /** if true a click on a dir will show its contents (only when not in selection) */
    var openDirs: Boolean = true

    var currentDir: MediaDir? = null
        set(value) {
            field = value

            if(value !== null)
                expandDir(value)
            else
                listAdapter.clear()
        }

    val selectedItems: List<MediaNode>
        get() = listAdapter.getSelectExtension().selectedItems.map {
            when(it) {
                is MediaFileItem -> it.file
                is MediaDirItem -> it.dir
                else -> throw IllegalStateException("unsupported item was in list")
            }
        }
    val inSelection: Boolean
        get() = listAdapter.getSelectExtension().selectedItems.isNotEmpty()

    lateinit var listAdapter: GenericFastItemAdapter
        private set

    private lateinit var listView: RecyclerView

    init {
        setupRecyclerViewAndAdapter()
        this.addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setSelectable(filesSelectable: Boolean, dirsSelectable: Boolean, multiselect: Boolean) {
        val select = listAdapter.getSelectExtension()

        val enableSelect = filesSelectable or dirsSelectable
        select.isSelectable = enableSelect
        select.multiSelect = multiselect and enableSelect
        select.allowDeselection = enableSelect
        select.selectOnLongClick = enableSelect

        select.selectionListener = object : ISelectionListener<GenericItem> {
            override fun onSelectionChanged(item: GenericItem, selected: Boolean) {
                when(item) {
                    is MediaFileItem -> {
                        if(selected && !filesSelectable){
                            select.deselectByIdentifier(item.identifier)
                        } else {
                            itemSelectionChangedListener?.invoke(item.file, selected)
                        }
                    }
                    is MediaDirItem -> {
                        if(selected && !dirsSelectable){
                            select.deselectByIdentifier(item.identifier)
                        } else {
                            itemSelectionChangedListener?.invoke(item.dir, selected)
                        }
                    }
                    else -> throw IllegalStateException("unsupported item was in list")
                }

                if(selected && !multiselect) {
                    // select.multiSelect does not check when an item is manually selected -> enforce here
                    if(select.selectedItems.size > 1){
                        select.deselect()
                        select.selectByIdentifier(item.identifier, false, true)
                    }
                }
            }
        }
    }

    fun clearSelection() {
        listAdapter.getSelectExtension().deselect()
    }

    fun goDirUp() {
        currentDir?.parent?.let {
            currentDir = it
        }
    }

    private fun setupRecyclerViewAndAdapter() {
        listAdapter = GenericFastItemAdapter().apply {
            setHasStableIds(true)

            // prevent click-event when long-click for selection
            onLongClickListener = { _, _, _, _ ->
                true
            }

            onClickListener =  { _, _, item, pos ->
                onItemClick(item, pos)
                true
            }
        }

        listView = RecyclerView(this.context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
        }
    }

    private fun onItemClick(item: GenericItem, pos: Int) {
        if (inSelection){
            if (notifyClickOnSelection)
                callOnClick(item)
            listAdapter.getSelectExtension().toggleSelection(pos)
        } else {
            if (item is MediaFileItem) {
                callOnClick(item)
            } else if (item is MediaDirItem) {
                if(notifyClickOnDir)
                    callOnClick(item)
                if(openDirs)
                    currentDir = item.dir
            } else {
                throw IllegalStateException("unsupported item was in list")
            }
        }
    }

    private fun callOnClick(item: GenericItem) {
        itemClickListener?.let {
            when(item){
                is MediaFileItem -> it(item.file)
                is MediaDirItem -> it(item.dir)
            }
        }
    }

    private fun expandDir(dir: MediaDir) {
        CoroutineScope(Dispatchers.IO).launch {
            dir.getDirs().sortedBy {
                it.name
            }.let { sortedDirs ->
                dir.getFiles().sortedBy {
                    it.name
                }.let { sortedFiles ->
                    sortedDirs + sortedFiles
                }
            }.mapNotNull(::mediaNodeToItem).let {
                withContext(Dispatchers.Main) {
                    listAdapter.setNewList(it)
                }
            }
        }
    }
}