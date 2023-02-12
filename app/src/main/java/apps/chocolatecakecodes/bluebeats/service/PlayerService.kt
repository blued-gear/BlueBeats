package apps.chocolatecakecodes.bluebeats.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.session.MediaSession
import androidx.media2.session.MediaSessionService
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
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
    private var notificationProvider: NotificationProvider by OnceSettable()

    override fun onCreate() {
        super.onCreate()

        player = VlcPlayer(VlcManagers.getLibVlc())
        setupSession()

        notificationProvider = NotificationProvider(this, session)

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
        return notificationProvider.createNotification()
    }

    private fun setupSession() {
        session = MediaSession.Builder(this, player).build()
        this.addSession(session)
    }
}

// code partly taken from androidx.media2.session.MediaNotificationHandler
@SuppressLint("PrivateResource")
private class NotificationProvider(
    private val context: Context,
    private val mediaSession: MediaSession
) {

    companion object {
        private val notificationId: Int = (PlayerService::javaClass.hashCode() shl 1) + 1
        private const val notificationChannelId = "PlayerService-notification_channel"
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private var lastThumb: Pair<String, Bitmap>? = null

    private val contentAction: PendingIntent
    private val playAction: NotificationCompat.Action
    private val pauseAction: NotificationCompat.Action
    private val skipBackAction: NotificationCompat.Action
    private val skipNextAction: NotificationCompat.Action

    private val audioPlaceholderImg: Bitmap by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_audiotrack_24)!!.toBitmap(256, 256)
    }
    private val otherPlaceholderImg: Bitmap by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_insert_drive_file_24)!!.toBitmap(256, 256)
    }

    init {
        contentAction = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.INTENT_OPTION_TAB, 2)
        }.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        playAction = NotificationCompat.Action(
            androidx.media2.session.R.drawable.media_session_service_notification_ic_play,
            context.getString(androidx.media2.session.R.string.play_button_content_description),
            createMediaPendingIntent(PlaybackStateCompat.ACTION_PLAY)
        )
        pauseAction = NotificationCompat.Action(
            androidx.media2.session.R.drawable.media_session_service_notification_ic_pause,
            context.getString(androidx.media2.session.R.string.pause_button_content_description),
            createMediaPendingIntent(PlaybackStateCompat.ACTION_PAUSE)
        )
        skipBackAction = NotificationCompat.Action(
            androidx.media2.session.R.drawable.media_session_service_notification_ic_skip_to_previous,
            context.getString(androidx.media2.session.R.string.skip_to_previous_item_button_content_description),
            createMediaPendingIntent(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )
        skipNextAction = NotificationCompat.Action(
            androidx.media2.session.R.drawable.media_session_service_notification_ic_skip_to_next,
            context.getString(androidx.media2.session.R.string.skip_to_next_item_button_content_description),
            createMediaPendingIntent(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )
    }

    fun createNotification(): MediaSessionService.MediaNotification {
        createNotificationChannel()

        val player = mediaSession.player as VlcPlayer
        return NotificationCompat.Builder(context, notificationChannelId).apply {
            setOnlyAlertOnce(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(player.isPlaying())
            setSmallIcon(R.mipmap.ic_launcher)

            player.currentMediaItem!!.let { media ->
                media.metadata!!.let { meta ->
                    setContentTitle(meta.getString(MediaMetadata.METADATA_KEY_TITLE))
                    setContentText(meta.getString(MediaMetadata.METADATA_KEY_ARTIST))
                }

                setLargeIcon(getThumbnail(player.getCurrentMedia()!!))
            }

            if(player.getCurrentPlaylist() !== null)
                addAction(skipBackAction)
            if(player.isPlaying())
                addAction(pauseAction)
            else
                addAction(playAction)
            if(player.getCurrentPlaylist() !== null)
                addAction(skipNextAction)
            setContentIntent(contentAction)

            MediaStyle().also {
                it.setMediaSession(mediaSession.sessionCompatToken)
                it.setShowCancelButton(false)
                it.setShowActionsInCompactView(0)
            }.let {
                this.setStyle(it)
            }
        }.let {
            MediaSessionService.MediaNotification(notificationId, it.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        if (notificationManager.getNotificationChannel(notificationChannelId) !== null)
            return

        NotificationChannelCompat.Builder(notificationChannelId, NotificationManagerCompat.IMPORTANCE_LOW).apply {
            setName(context.getString(R.string.app_name))
        }.let {
            notificationManager.createNotificationChannel(it.build())
        }
    }

    private fun getThumbnail(media: MediaFile): Bitmap {
        lastThumb?.let {
            if(it.first == media.path)
                return it.second
            else
                lastThumb = null
        }

        return VlcManagers.getMediaDB().getSubject().getThumbnail(media, -1, -1).let {
            if(it !== null) {
                it
            } else if(media.type == MediaFile.Type.AUDIO) {
                audioPlaceholderImg
            } else {
                otherPlaceholderImg
            }
        }.also {
            lastThumb = Pair(media.path, it)
        }
    }

    private fun createMediaPendingIntent(action: Long): PendingIntent? {
        val keyCode = PlaybackStateCompat.toKeyCode(action)
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(context, PlayerService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }

        return PendingIntent.getService(
            context,
            keyCode, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
