package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.Playlist
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType

internal typealias PlaylistInfo = Triple<String, PlaylistType, Long>

internal class PlaylistsViewModel : ViewModel() {

    val showOverview = MutableLiveData<Boolean>(true)
    var selectedPlaylist: Playlist? = null
    var allLists: List<PlaylistInfo>? = null
    var playlistItems: List<MediaFile>? = null
    var selectedMedia: MediaFile? = null
}
