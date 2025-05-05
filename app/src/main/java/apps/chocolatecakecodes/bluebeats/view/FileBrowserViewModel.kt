package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.util.Utils

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
        Utils.trySetValueImmediately(currentDirRW, dir)
    }

    fun selectFile(file: MediaFile?){
        Utils.trySetValueImmediately(selectedFileRW, file)
    }
}