package apps.chocolatecakecodes.bluebeats.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.view.MainActivity
import com.google.common.collect.ImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlayerService : MediaSessionService(){

    companion object {
        const val INTENT_INTERNAL_BINDER = "apps.chocolatecakecodes.bluebeats.service.PlayerService.internalBinder"

        private const val LOG_TAG = "PlayerService"

        fun connect(ctx: Context): PlayerServiceConnection {
            val conn = PlayerServiceConnection()

            ctx.startService(Intent(ctx.applicationContext, PlayerService::class.java))
            ctx.bindService(Intent(ctx, PlayerService::class.java).apply {
                action = INTENT_INTERNAL_BINDER
            }, conn, Context.BIND_AUTO_CREATE)

            return conn
        }
    }

    private lateinit var player: VlcPlayer
    private var session: MediaSession by OnceSettable()
    private var initialized = false

    override fun onCreate() {
        super.onCreate()

        if(!VlcManagers.isInitialized()) {
            Log.w(LOG_TAG, "VlcManagers nit initialized, even if they should; calling init")
            try {
                VlcManagers.init(this)
            }catch(e: IllegalStateException) {
                Log.w(LOG_TAG, "init failed (maybe it got already initialized)", e)
            }
        }

        player = VlcPlayer(VlcManagers.getLibVlc(), this.mainLooper)
        setMediaNotificationProvider(NotificationProvider(this) { session })// notificationProvider must be set before setupSession() but needs a reference to the session, so set it lazily
        setupSession()

        initialized = true
    }

    override fun onDestroy() {
        initialized = false
        session.release()
        player.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        val superRet = super.onBind(intent)

        return if(intent?.action == INTENT_INTERNAL_BINDER)
            PlayerServiceBinder()
        else
            superRet
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return session
    }

    private fun setupSession() {
        session = MediaSession.Builder(this, player).build()
        this.addSession(session)
    }

    internal inner class PlayerServiceBinder : Binder() {
        val player = this@PlayerService.player
    }
}

internal class PlayerServiceConnection : ServiceConnection {

    var player: VlcPlayer? = null
        private set

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        player = (service as PlayerService.PlayerServiceBinder).player
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        player = null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class NotificationProvider(
    private val context: Context,
    private val mediaSessionProvider: () -> MediaSession
) : MediaNotification.Provider {

    companion object {
        private val notificationId: Int = (PlayerService::javaClass.hashCode() shl 1) + 1
        private const val notificationChannelId = "PlayerService-notification_channel"
    }

    private val mediaSession: MediaSession by lazy { mediaSessionProvider() }
    private val notificationManager = NotificationManagerCompat.from(context)
    private val contentActionIntent: PendingIntent
    private var lastThumb: Pair<String, Bitmap>? = null

    private val audioPlaceholderImg: Bitmap by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_audiotrack_24)!!.toBitmap(256, 256)
    }
    private val otherPlaceholderImg: Bitmap by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_insert_drive_file_24)!!.toBitmap(256, 256)
    }

    init {
        contentActionIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.INTENT_OPTION_TAB, 2)
        }.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        createNotificationChannel()

        val player = mediaSession.player as VlcPlayer
        return NotificationCompat.Builder(context, notificationChannelId).apply {
            setOnlyAlertOnce(true)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(player.isPlaying)
            setSmallIcon(R.mipmap.ic_launcher)

            player.currentMediaItem!!.let { media ->
                setContentTitle(media.mediaMetadata.title)
                setContentText(media.mediaMetadata.artist)

                setLargeIcon(getThumbnail(player.getCurrentMedia()!!))
            }

            createActions(this, actionFactory)
            setContentIntent(contentActionIntent)

            MediaStyleNotificationHelper.MediaStyle(mediaSession).also {
                it.setShowCancelButton(false)
                it.setShowActionsInCompactView(1)
            }.let {
                this.setStyle(it)
            }
        }.let {
            MediaNotification(notificationId, it.build())
        }
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        return false
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

    private fun createActions(builder: NotificationCompat.Builder, factory: MediaNotification.ActionFactory) {
        val player = mediaSession.player as VlcPlayer

        builder.addAction(factory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_previous),
            context.getString(androidx.media3.session.R.string.media3_controls_seek_to_previous_description),
            Player.COMMAND_SEEK_TO_PREVIOUS
        ))
        if(player.isPlaying) {
            builder.addAction(factory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_pause),
                context.getString(androidx.media3.session.R.string.media3_controls_pause_description),
                Player.COMMAND_PLAY_PAUSE
            ))
        } else {
            builder.addAction(factory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_play),
                context.getString(androidx.media3.session.R.string.media3_controls_play_description),
                Player.COMMAND_PLAY_PAUSE
            ))
        }
        builder.addAction(factory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, androidx.media3.session.R.drawable.media3_icon_next),
            context.getString(androidx.media3.session.R.string.media3_controls_seek_to_next_description),
            Player.COMMAND_SEEK_TO_NEXT
        ))
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
}
