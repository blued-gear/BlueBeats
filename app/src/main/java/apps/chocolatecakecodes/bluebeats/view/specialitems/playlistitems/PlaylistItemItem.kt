package apps.chocolatecakecodes.bluebeats.view.specialitems.playlistitems

import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.TimeSpanItem
import apps.chocolatecakecodes.bluebeats.view.specialitems.SelectableItem
import com.mikepenz.fastadapter.IItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem as MediaFilePlItem

internal sealed interface PlaylistItemItem<VH : RecyclerView.ViewHolder> : IItem<VH> {
    val item: PlaylistItem
}

internal fun itemForPlaylistItem(itm: PlaylistItem, draggable: Boolean): SelectableItem<out SelectableItem.ViewHolder<*>> {
    return when(itm) {
        is MediaFilePlItem -> MediaFileItemItem(itm, draggable)
        is TimeSpanItem -> TimeSpanItemItem(itm, draggable)
        is PlaylistItem.INVALID, TimeSpanItem.INVALID ->
            throw AssertionError("not all PlaylistItem implementations were handled")
    }
}
