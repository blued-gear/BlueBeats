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
        currentDirRW.postValue(dir)
    }

    fun selectFile(file: MediaFile){
        selectedFileRW.postValue(file)
    }
}