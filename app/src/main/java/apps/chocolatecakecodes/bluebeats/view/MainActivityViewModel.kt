package apps.chocolatecakecodes.bluebeats.view

import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.lifecycle.*

internal class MainActivityViewModel : ViewModel(){

    enum class Tabs{
        MEDIA, PLAYER, PLAYLISTS
    }

    enum class Dialogs(val tag: String){
        NONE(""), FILE_DETAILS("dlg-file_details")
    }

    val currentTab = MutableLiveData<Tabs>(Tabs.MEDIA)
    val fullScreenContent = MutableLiveData<View>()
    val menuProvider = MutableLiveData<((Menu, MenuInflater) -> Unit)?>()
    val currentDialog = MutableLiveData<Dialogs>(Dialogs.NONE)

    private val backPressListeners = MutableList<BackPressListener>(0){throw AssertionError("none should be initialized")}

    fun addBackPressListener(owner: LifecycleOwner, exec: () -> Unit){
        val listener = BackPressListener(owner.lifecycle, exec)
        backPressListeners.add(listener)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver{
            override fun onDestroy(owner: LifecycleOwner) {
                backPressListeners.remove(listener)
            }
        })
    }

    fun onBackPressed(){
        backPressListeners.forEach {
            it.onBackPressed()
        }
    }

    private class BackPressListener(private val lifecycle: Lifecycle, private val exec: () -> Unit){
        fun onBackPressed(){
            try {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    exec()
            }catch (e: Exception){
                Log.e("MainActivity/BackPressListener", "listener threw exception (this is forbidden)", e)
            }
        }
    }

}