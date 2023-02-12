package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

internal class PlayerViewModel : ViewModel() {

    private val isFullscreenRW: MutableLiveData<Boolean> = MutableLiveData()
    val isFullscreen: LiveData<Boolean> = isFullscreenRW// public read-only property
    private val timeTextAsRemainingRW: MutableLiveData<Boolean> = MutableLiveData(false)
    /** if true show the remaining time, instead of the current time, in the player-progress */
    val timeTextAsRemaining: LiveData<Boolean> = timeTextAsRemainingRW// public read-only property

    fun setFullscreenMode(fullscreen: Boolean){
        isFullscreenRW.postValue(fullscreen)
    }

    fun setTimeTextAsRemaining(value: Boolean){
        timeTextAsRemainingRW.postValue(value)
    }
}
