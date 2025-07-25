package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.util.castToOrNull
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

internal fun mediaNodeToItem(node: MediaNode, showThumb: Boolean = false): AbstractItem<*>? = when(node) {
    is MediaDir -> MediaDirItem(node)
    is MediaFile -> MediaFileItem(node, showThumb = showThumb)
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
    /** if true items can be selected with a simple click */
    var startSelectionWithClick: Boolean = false
    /** if true all files will get a thumbnail or a placeholder if none could be generated */
    var showThumb: Boolean = true

    var currentDir: MediaDir? = null
        set(value) {
            field = value

            if(value !== null)
                expandDir(value)
            else
                listAdapter.clear()

            inDeselectClick = false
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

    private lateinit var listAdapter: GenericFastItemAdapter
    private lateinit var listView: RecyclerView
    private var inDeselectClick = false

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

                inDeselectClick = !selected
            }
        }
    }

    fun selectAll() {
        listAdapter.adapterItems
            .filterIsInstance<MediaFileItem>()
            .forEach {
                listAdapter.getSelectExtension().select(it, true)
            }
    }

    fun clearSelection() {
        listAdapter.getSelectExtension().deselect()
    }

    fun goDirUp(): MediaDir? {
        currentDir?.parent?.castToOrNull<MediaDir>()?.let {
            currentDir = it
            return it
        }
        return null
    }

    fun addNode(node: MediaNode): Boolean {
        listAdapter.adapterItems.any {
            if(node is MediaDir && it is MediaDirItem) {
                it.dir.id == node.id
            } else if(node is MediaFile && it is MediaFileItem) {
                it.file.id == node.id
            } else {
                false
            }
        }.let {  containsNode ->
            if(!containsNode) {
                mediaNodeToItem(node, showThumb)?.let {
                    listAdapter.add(it)
                }
            }

            return !containsNode
        }
    }

    fun removeNode(node: MediaNode): Boolean {
        listAdapter.adapterItems.indexOfFirst {
            if(node is MediaDir && it is MediaDirItem) {
                it.dir.id == node.id
            } else if(node is MediaFile && it is MediaFileItem) {
                it.file.id == node.id
            } else {
                false
            }
        }.let {  idx ->
            if(idx != -1) {
                listAdapter.remove(idx)
                return true
            } else {
                return false
            }
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

        if(startSelectionWithClick && !inDeselectClick && !inSelection)
            listAdapter.getSelectExtension().select(pos)
        inDeselectClick = false
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
            dir.getDirs().sorted().let { sortedDirs ->
                dir.getFiles().sorted().let { sortedFiles ->
                    sortedDirs + sortedFiles
                }
            }.mapNotNull {
                mediaNodeToItem(it, showThumb)
            }.let {
                withContext(Dispatchers.Main) {
                    listAdapter.setNewList(it)
                }
            }
        }
    }
}