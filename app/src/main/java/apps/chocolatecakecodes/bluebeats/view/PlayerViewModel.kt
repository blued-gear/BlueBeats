package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.taglib.Chapter

class PlayerViewModel : ViewModel() {

    private val currentMediaRW: MutableLiveData<MediaFile> = MutableLiveData(null)
    public val currentMedia: LiveData<MediaFile> = currentMediaRW// public read-only property
    private val isPlayingRW: MutableLiveData<Boolean> = MutableLiveData(false)
    public val isPlaying: LiveData<Boolean> = isPlayingRW// public read-only property
    private val playPosRW: MutableLiveData<Long> = MutableLiveData(0)
    public val playPos: LiveData<Long> = playPosRW// public read-only property
    private val isFullscreenRW: MutableLiveData<Boolean> = MutableLiveData()
    public val isFullscreen: LiveData<Boolean> = isFullscreenRW// public read-only property
    private val timeTextAsRemainingRW: MutableLiveData<Boolean> = MutableLiveData(false)
    /** if true show the remaining time, instead of the current time, in the player-progress */
    public val timeTextAsRemaining: LiveData<Boolean> = timeTextAsRemainingRW// public read-only property
    private val chaptersRW: MutableLiveData<List<Chapter>> = MutableLiveData()
    public val chapters: LiveData<List<Chapter>> = chaptersRW// public read-only property

    fun play(media: MediaFile){
        currentMediaRW.postValue(media)
        updatePlayPosition(0)
        resume()
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
}
