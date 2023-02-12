package apps.chocolatecakecodes.bluebeats.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.media2.common.MediaItem
import androidx.media2.common.SessionPlayer
import androidx.media2.session.MediaSession
import androidx.media2.session.MediaSessionService
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.view.MainActivity

internal class PlayerService : MediaSessionService(){

    companion object {
        // really not the best way but works for my usecase
        private var instance: PlayerService? = null

        fun getInstancePlayer(): VlcPlayer {
            return instance?.player ?: throw IllegalStateException("service not running")
        }
    }

    private lateinit var player: VlcPlayer
    private var session: MediaSession by OnceSettable()
    private var notificationManager: NotificationManager by OnceSettable()

    override fun onCreate() {
        super.onCreate()

        player = VlcPlayer(VlcManagers.getLibVlc())
        setupSession()

        instance = this
    }

    override fun onDestroy() {
        instance = null

        session.close()
        player.release()

        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return session
    }

    override fun onUpdateNotification(session: MediaSession): MediaNotification? {
        if(player.playerState == SessionPlayer.PLAYER_STATE_IDLE)
            return null
        return super.onUpdateNotification(session)
    }

    private fun setupSession() {
        val openPlayerIntent = TaskStackBuilder.create(this).run {
            addNextIntent(Intent(this@PlayerService, MainActivity::class.java))
            //TODO change MainActivity so that it can handle events requesting tabs
            //TODO request player-tab with this intent

            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)!!
        }

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayerIntent)
            .build()
        this.addSession(session)
    }
}
