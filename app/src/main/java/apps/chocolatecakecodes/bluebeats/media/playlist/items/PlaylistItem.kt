package apps.chocolatecakecodes.bluebeats.media.playlist.items

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer

internal sealed interface PlaylistItem {

    /** the MediaFile which will be played, or <code>null</code> if this item does not use a file */
    val file: MediaFile?

    fun play(player: VlcPlayer)

    /** marker for items which could not be loaded (for example when the referenced file was removed) */
    sealed interface INVALID : PlaylistItem
}
