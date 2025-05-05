package apps.chocolatecakecodes.bluebeats

import android.app.Application
import android.content.Context
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.log.Logger
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaLibrary
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.TimeSpanItemPlayerController
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.storage.RuleStorage
import apps.chocolatecakecodes.bluebeats.media.playlist.impl.LoggerImpl
import apps.chocolatecakecodes.bluebeats.media.playlist.impl.MediaLibraryImpl
import apps.chocolatecakecodes.bluebeats.media.playlist.impl.RuleStorageImpl
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class Application : Application() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)

        setupAcra()
        setupPlaylistLib()
    }

    private fun setupAcra() {
        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            mailSender {
                mailTo = "chocolatecakecodes@disroot.org"
                reportAsFile = true
                reportFileName = "BlueBeats-Crash.json"
                subject = "BlueBeats: Crash Report"
                body = "BlueBeats has crashed an this report was generated automatically.\n\n" +
                        "----- Optionally, insert more details below this line -----\n\n"
            }

            dialog {
                text = this@Application.getString(R.string.crash_dlg_message)
                title = this@Application.getString(R.string.crash_dlg_title)
                positiveButtonText = this@Application.getString(R.string.misc_yes)
                negativeButtonText = this@Application.getString(R.string.misc_no)
            }
        }
    }

    private fun setupPlaylistLib() {
        Logger.Slot.INSTANCE = LoggerImpl()
        MediaLibrary.Slot.INSTANCE = MediaLibraryImpl()
        RuleStorage.Slot.INSTANCE = RuleStorageImpl()
        TimeSpanItemPlayerController.Factory.Slot.INSTANCE = TimeSpanItemPlayerController.Factory{ apps.chocolatecakecodes.bluebeats.media.playlist.TimeSpanItemPlayerController() }
    }
}
