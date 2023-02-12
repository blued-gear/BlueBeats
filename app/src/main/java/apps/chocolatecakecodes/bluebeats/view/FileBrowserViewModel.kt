package apps.chocolatecakecodes.bluebeats.view

import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile

internal class FileBrowserViewModel : ViewModel() {

    val mediaDB = VlcManagers.getMediaDB()
    var mediaWasScanned: Boolean = false
        private set
    private val currentDirRW = MutableLiveData<MediaDir>()
    val currentDir: LiveData<MediaDir> = currentDirRW
    private val selectedFileRW = MutableLiveData<MediaFile?>()
    val selectedFile: LiveData<MediaFile?> = selectedFileRW
    val storagePermissionsGranted = MutableLiveData<Boolean>()

    fun mediaScanned(){
        mediaWasScanned = true
    }

    fun setCurrentDir(dir: MediaDir){
        if(isMainThread())
            currentDirRW.setValue(dir)
        else
            currentDirRW.postValue(dir)
    }

    fun selectFile(file: MediaFile?){
        if(isMainThread())
            selectedFileRW.setValue(file)
        else
            selectedFileRW.postValue(file)
    }

    private fun isMainThread(): Boolean{
        return Looper.myLooper() == Looper.getMainLooper()
    }
}