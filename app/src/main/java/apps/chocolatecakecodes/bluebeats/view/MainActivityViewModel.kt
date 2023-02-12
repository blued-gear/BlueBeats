package apps.chocolatecakecodes.bluebeats.view

import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel(){

    enum class Tabs{
        MEDIA, PLAYER
    }

    val currentTab = MutableLiveData<Tabs>(Tabs.MEDIA)
    val fullScreenContent = MutableLiveData<View>()

}