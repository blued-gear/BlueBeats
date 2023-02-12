package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile

class FileBrowserViewModel : ViewModel() {

    public val mediaDB = VlcManagers.getMediaDB()
    public var mediaWasScanned: Boolean = false
        private set
    private val currentDirRW = MutableLiveData<MediaDir>()
    public val currentDir: LiveData<MediaDir> = currentDirRW
    private val selectedFileRW = MutableLiveData<MediaFile>()
    public val selectedFile: LiveData<MediaFile> = selectedFileRW

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