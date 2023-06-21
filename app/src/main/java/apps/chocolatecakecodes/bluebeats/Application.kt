package apps.chocolatecakecodes.bluebeats

import android.app.Application
import android.content.Context
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class Application : Application() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)

        setupAcra()
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
}
