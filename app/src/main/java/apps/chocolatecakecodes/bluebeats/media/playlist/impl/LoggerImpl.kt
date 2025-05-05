package apps.chocolatecakecodes.bluebeats.media.playlist.impl

import android.util.Log
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.log.Logger

internal class LoggerImpl : Logger {

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}