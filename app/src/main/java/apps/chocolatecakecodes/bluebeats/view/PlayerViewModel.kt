package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.taglib.Chapter

internal class PlayerViewModel : ViewModel() {

    private val currentMediaRW: MutableLiveData<MediaFile> = MutableLiveData(null)
    val currentMedia: LiveData<MediaFile> = currentMediaRW// public read-only property
    private val currentPlaylistRW: MutableLiveData<PlaylistIterator?> = MutableLiveData(null)
    val currentPlaylist: LiveData<PlaylistIterator?> = currentPlaylistRW// public read-only property
    private val isPlayingRW: MutableLiveData<Boolean> = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = isPlayingRW// public read-only property
    private val playPosRW: MutableLiveData<Long> = MutableLiveData(0)
    val playPos: LiveData<Long> = playPosRW// public read-only property
    private val isFullscreenRW: MutableLiveData<Boolean> = MutableLiveData()
    val isFullscreen: LiveData<Boolean> = isFullscreenRW// public read-only property
    private val timeTextAsRemainingRW: MutableLiveData<Boolean> = MutableLiveData(false)
    /** if true show the remaining time, instead of the current time, in the player-progress */
    val timeTextAsRemaining: LiveData<Boolean> = timeTextAsRemainingRW// public read-only property
    private val chaptersRW: MutableLiveData<List<Chapter>> = MutableLiveData()
    val chapters: LiveData<List<Chapter>> = chaptersRW// public read-only property

    fun play(media: MediaFile){
        currentPlaylistRW.postValue(null)
        setCurrentMediaAndPlay(media)
    }

    fun playPlaylist(pl: PlaylistIterator) {
        currentPlaylistRW.postValue(pl)
        setCurrentMediaAndPlay(pl.nextMedia())
    }

    fun pause(){
        isPlayingRW.postValue(false)
    }

    fun resume(){
        isPlayingRW.postValue(true)
    }

    fun updatePlayPosition(time: Long){
        playPosRW.postValue(time)
    }

    fun setFullscreenMode(fullscreen: Boolean){
        isFullscreenRW.postValue(fullscreen)
    }

    fun setTimeTextAsRemaining(value: Boolean){
        timeTextAsRemainingRW.postValue(value)
    }

    fun setChapters(value: List<Chapter>){
        chaptersRW.postValue(value)
    }

    private fun setCurrentMediaAndPlay(media: MediaFile) {
        currentMediaRW.postValue(media)
        updatePlayPosition(0)
        resume()
    }
}
