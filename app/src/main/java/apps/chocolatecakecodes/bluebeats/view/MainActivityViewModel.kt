package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel(){

    enum class Tabs{
        MEDIA, PLAYER
    }

    val currentTab = MutableLiveData<Tabs>(Tabs.MEDIA)

}