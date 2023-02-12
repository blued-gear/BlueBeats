package apps.chocolatecakecodes.bluebeats.util

import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class SmartBackPressedCallback(private val lifecycle: Lifecycle, private val callback: () -> Unit)
    : OnBackPressedCallback(false), LifecycleEventObserver {

    init {
        lifecycle.addObserver(this)
    }

    override fun handleOnBackPressed() {
        callback()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if(event == Lifecycle.Event.ON_RESUME)
            isEnabled = true
        else if (event == Lifecycle.Event.ON_PAUSE)
            isEnabled = false
        else if(event == Lifecycle.Event.ON_DESTROY)
            lifecycle.removeObserver(this)
    }
}
