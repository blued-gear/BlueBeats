package apps.chocolatecakecodes.bluebeats.view.specialitems.playlistitems

import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.MediaFileItem as MediaFilePlItem

internal class MediaFileItemItem(
    override val item: MediaFilePlItem,
    isDraggable: Boolean = false
) : PlaylistItemItem<MediaFileItem.ViewHolder>,
    MediaFileItem(item.file, isDraggable, true, true) {

    override val type: Int = MediaFileItemItem::class.hashCode()
}