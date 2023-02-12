package apps.chocolatecakecodes.bluebeats.view

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.lifecycle.*

internal class MainActivityViewModel : ViewModel(){

    enum class Tabs{
        MEDIA, PLAYER, PLAYLISTS
    }

    enum class Dialogs(val tag: String){
        NONE(""),
        FILE_DETAILS("dlg-file_details"),
        DYNPLAYLIST_EDITOR("dlg-dynpl_edit")
    }

    val currentTab = MutableLiveData<Tabs>(Tabs.MEDIA)
    val fullScreenContent = MutableLiveData<View>()
    val menuProvider = MutableLiveData<((Menu, MenuInflater) -> Unit)?>()
    val currentDialog = MutableLiveData<Dialogs>(Dialogs.NONE)
}
