package apps.chocolatecakecodes.bluebeats.media.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.media.playlist.TempPlaylist
import apps.chocolatecakecodes.bluebeats.media.playlist.UNDETERMINED_COUNT
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.TimeSpanItem
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*
import kotlin.concurrent.Volatile

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VlcPlayer(libVlc: ILibVLC, looper: Looper) : SimpleBasePlayer(looper), MediaPlayer.EventListener {

    companion object {
        private const val LOG_TAG = "VlcPlayer"
    }

    var seekAmount = 5000L
        set(value) {
            field = value

            synchronized(state) {
                state.setSeekBackIncrementMs(value)
                state.setSeekForwardIncrementMs(value)
                mainThreadHandler.post { this.invalidateState() }
            }
        }
    var maxSeekBackUntilPrev = 2000L
        set(value) {
            field = value

            synchronized(state) {
                state.setMaxSeekToPreviousPositionMs(value)
                mainThreadHandler.post { this.invalidateState() }
            }
        }

    private val player = MediaPlayer(libVlc)
    private val mainThreadHandler = Handler(looper)
    private val chapters = ArrayList<Chapter>()
    private val chaptersRO = Collections.unmodifiableList(chapters)
    private val state: SimpleBasePlayer.State.Builder
    private val currentPlaylist = RequireNotNull<PlaylistIterator>()
    private var suppressCallbacks: Boolean = false
    @Volatile
    private var playbackPos: Long = 0
    private var repeatMode: Int = Player.REPEAT_MODE_OFF//TODO once #3 is implemented rewrite this + currentMedia to a ad-hoc created playlist for single files if jus a file is playing

    private val supportedCommands = Commands.Builder().addAll(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SET_SHUFFLE_MODE,
        Player.COMMAND_SET_REPEAT_MODE,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_RELEASE,
        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
    ).build()

    init {
        player.setEventListener(this)

        state = State.Builder().apply {
            setAvailableCommands(supportedCommands)

            setSeekBackIncrementMs(seekAmount)
            setSeekForwardIncrementMs(seekAmount)
            setMaxSeekToPreviousPositionMs(maxSeekBackUntilPrev)

            setContentPositionMs(this@VlcPlayer::getTimePos)
            setPlaybackState(Player.STATE_IDLE)

            // set initial dummy-PL (because the order of VLC loading events doesn't play nice with the media3 requirements)
            setPlaylist(listOf(MediaItemData.Builder("").build()))
        }
    }

    //region other public methods
    fun playMedia(media: MediaFile, keepPlaylist: Boolean = false) {
        if(!keepPlaylist) {
            playPlaylist(TempPlaylist(media))
        } else {

            chapters.clear()

            playbackPos = 0
            player.play(media.path)

            // state will be invalidated by VLC event
        }
    }

    fun playPlaylist(playlist: PlaylistIterator) {
        currentPlaylist.set(playlist)
        repeatMode = if(playlist.repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

        synchronized(state) {
            state.setRepeatMode(if(playlist.repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF)
            state.setShuffleModeEnabled(playlist.shuffle)
        }

        playlist.nextItem().play(this)

        // state will be invalidated by VLC event
    }

    fun setVideoOutput(out: VLCVideoLayout) {
        if(player.vlcVout.areViewsAttached())
            player.detachViews()
        player.attachViews(out, null, false, false)//TODO in future version subtitle option should be settable
    }

    fun clearVideoOutput() {
        player.detachViews()
    }

    fun getChapters(): List<Chapter> {
        return chaptersRO
    }

    fun getCurrentMedia(): MediaFile? {
        return currentPlaylist.getNullable()?.currentItem()?.file
    }

    fun getCurrentPlaylist(): PlaylistIterator? {
        return currentPlaylist.getNullable()
    }
    //endregion

    //region media3 player getters
    override fun getState(): State {
        return synchronized(state) {
            state.apply {
                this.setRepeatMode(repeatMode)
                this.setShuffleModeEnabled(currentPlaylist.getNullable()?.shuffle ?: false)
            }.build()
        }
    }

    private fun getTimePos(): Long {
        return playbackPos
    }
    //endregion

    //region media3 player commands
    override fun handleRelease(): ListenableFuture<*> {
        this.stop()
        player.release()

        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if(playWhenReady) {
            return handlePlay()
        } else {
            return handlePause()
        }
    }

    override fun handleStop(): ListenableFuture<*> {
        player.stop()

        currentPlaylist.set(null)
        playbackPos = 0
        repeatMode = Player.REPEAT_MODE_OFF

        synchronized(state) {
            state.setPlaybackState(Player.STATE_IDLE)
                .setTotalBufferedDurationMs(PositionSupplier.ZERO)
                .setContentBufferedPositionMs { C.TIME_UNSET }
                .setIsLoading(false)
        }
        
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val ret = SettableFuture.create<Unit>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pl = currentPlaylist.get()

                if(pl.currentPosition == UNDETERMINED_COUNT) {
                    ret.setException(IllegalStateException("playlist not started"))
                    return@launch
                }

                if(mediaItemIndex != pl.currentPosition) {
                    pl.seek(mediaItemIndex - pl.currentPosition)
                    pl.currentItem().play(this@VlcPlayer)
                }

                if(positionMs != C.TIME_UNSET) {
                    //TODO this might not work as the player might still be loading
                    player.setTime(positionMs, true)
                }

                synchronized(state) {
                    state.setCurrentMediaItemIndex(pl.currentPosition)
                }
                ret.set(Unit)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "exception in handleSeek()::pl_seek", e)
                ret.setException(e)
            }
        }
        return ret
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        currentPlaylist.get().repeat = repeatMode != Player.REPEAT_MODE_OFF

        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        // on DynamicPlaylist this will trigger regeneration, so run it outside of main-thread
        val ret = SettableFuture.create<Unit>()
        CoroutineScope(Dispatchers.IO).launch {
            currentPlaylist.get().shuffle = shuffleModeEnabled
            ret.set(Unit)
        }
        return ret
    }

    private fun handlePlay(): ListenableFuture<*> {
        if(currentPlaylist.isNull()) {
            return Futures.immediateFailedFuture<Void?>(IllegalStateException("no media set"))
        }

        player.play()

        synchronized(state) {
            state.setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            state.setPlaybackState(Player.STATE_READY)
        }

        return Futures.immediateVoidFuture()
    }

    private fun handlePause(): ListenableFuture<*> {
        if(currentPlaylist.isNull()) {
            return Futures.immediateFailedFuture<Void?>(IllegalStateException("no media set"))
        }

        player.pause()

        synchronized(state) {
            state.setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }

        return Futures.immediateVoidFuture()
    }
    //endregion

    //region vlc events
    override fun onEvent(event: MediaPlayer.Event?) {
        if(suppressCallbacks)
            return

        event?.let {
            when(it.type) {
                MediaPlayer.Event.Playing, MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> onPlayingChanged()
                MediaPlayer.Event.EndReached -> onEndReached()
                /*MediaPlayer.Event.MediaChanged -> onMediaChanged()
                MediaPlayer.Event.LengthChanged -> onMediaLoaded()*/
                MediaPlayer.Event.Opening -> onMediaLoading()
                MediaPlayer.Event.LengthChanged -> onMediaLoaded()
                MediaPlayer.Event.TimeChanged -> onTimeChanged(it.timeChanged)
                MediaPlayer.Event.EncounteredError -> onPlayerError()
                else -> Unit// ignore
            }
        }
    }

    private fun onPlayingChanged() {
        synchronized(state) {
            state.setPlayWhenReady(player.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            mainThreadHandler.post { invalidateState() }
        }
    }

    private fun onMediaLoading() {
        synchronized(state) {
            state.setPlayWhenReady(player.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            state.setPlaybackState(Player.STATE_BUFFERING)
            state.setIsLoading(true)

            mainThreadHandler.post { invalidateState() }
        }
    }

    private fun onMediaLoaded() {
        // in IO-thread because fillInStateMedia() will load media-attrs from DB
        CoroutineScope(Dispatchers.IO).launch {
            // chapter-info should now be loaded
            loadChapters()

            synchronized(state) {
                fillInStateMedia(state)
                state.setPlaybackState(Player.STATE_READY)
                state.setIsLoading(false)

                mainThreadHandler.post { invalidateState() }
            }
        }
    }

    private fun onEndReached() {
        if(!currentPlaylist.get().isAtEnd()) {
            playNextPlaylistItem()
        } else {
            onTotalEndReached()
        }
    }

    private fun onTotalEndReached() {
        // reset player, or else seek will break
        suppressCallbacks = true
        currentPlaylist.get().currentItem().file?.let {
            player.play(it.path)
        } ?: let {
            currentPlaylist.get().currentItem().play(this)
        }
        player.pause()
        playbackPos = 0
        suppressCallbacks = false

        synchronized(state) {
            state.setPlaybackState(Player.STATE_ENDED)
            state.setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
            mainThreadHandler.post { invalidateState() }
        }
    }

    private fun onTimeChanged(time: Long) {
        playbackPos = time
    }

    private fun onPlayerError() {
        Log.e(LOG_TAG, "received error-event")

        synchronized(state) {
            state.setPlayerError(PlaybackException("VLC reported an error", null, PlaybackException.ERROR_CODE_UNSPECIFIED))
            mainThreadHandler.post { invalidateState() }
        }
    }
    //endregion

    //region private methods
    private fun playNextPlaylistItem() {
        if(repeatMode == Player.REPEAT_MODE_ONE) {
            currentPlaylist.get().currentItem().play(this)
        } else {
            // nextItem() could trigger DB actions
            CoroutineScope(Dispatchers.IO).launch {
                currentPlaylist.get().nextItem().play(this@VlcPlayer)
            }
        }
    }

    private fun loadChapters() {
        chapters.clear()

        val file = currentPlaylist.get().currentItem().file ?: return

        // 1.: use chapters from MediaFile; 2.: if not available use from player; 3.: leave empty
        val mediaChapters = file.chapters
        val chapters = if(!mediaChapters.isNullOrEmpty()){
            mediaChapters
        } else {
            val vlcChapters: Array<MediaPlayer.Chapter>? = player.getChapters(-1)
            if(vlcChapters !== null) {
                vlcChapters.map {
                    Chapter(it.timeOffset, it.timeOffset + it.duration, it.name)
                }
            } else {
                emptyList()
            }
        }

        this.chapters.addAll(chapters)
    }

    //region convert PlaylistItem to MediaItemData
    private fun fillInStateMedia(builder: State.Builder) {
        // set here as a DynamicPlaylist might have updated itself
        val plItems = currentPlaylist.get().getItems()
        plItems.mapIndexed { idx, itm ->
            playlistItemToMediaItemData(itm, idx)
        }.let {
            builder.setPlaylist(it)
        }

        builder.setCurrentMediaItemIndex(currentPlaylist.get().currentPosition)
    }

    private fun playlistItemToMediaItemData(itm: PlaylistItem, itmIdx: Int): MediaItemData {
        val id = Objects.hash(itm, itmIdx)
        return MediaItemData.Builder(id).apply {
            this.setIsSeekable(true)

            this.setMediaItem(playlistItemToMediaItem(itm))
            this.setDurationUs(itm.file?.mediaTags?.length?.times(1000L) ?: C.TIME_UNSET)

            itm.file?.let { file ->
                this.setPeriods(mediaFilePeriods(file))
            }
        }.build()
    }

    private fun playlistItemToMediaItem(itm: PlaylistItem): MediaItem {
        return MediaItem.Builder().apply {
            itm.file?.let { file ->
                fillInMediaItemFromMediaFile(file, this)
            }

            if(itm is TimeSpanItem) {
                ClippingConfiguration.Builder().apply {
                    this.setStartPositionMs(itm.startMs)
                    this.setEndPositionMs(itm.endMs)
                    this.setRelativeToDefaultPosition(true)
                }.let {
                    this.setClippingConfiguration(it.build())
                }
            }
        }.build()
    }

    private fun fillInMediaItemFromMediaFile(file: MediaFile, builder: MediaItem.Builder) {
        builder.setMediaId(file.entityId.toString())
        builder.setUri("file://${file.path}")

        MediaMetadata.Builder().apply {
            when(file.type) {
                MediaFile.Type.AUDIO -> this.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                MediaFile.Type.VIDEO -> this.setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                else -> this.setMediaType(null)
            }

            this.setIsPlayable(true)
            this.setIsBrowsable(false)

            this.setTitle(file.title)
            file.mediaTags.artist.let {
                if(!it.isNullOrEmpty())
                    this.setArtist(it)
            }
            file.mediaTags.genre.let {
                if(!it.isNullOrEmpty())
                    this.setGenre(it)
            }

            // chapters should now be available, so make sure that onMediaMetadataChanged() gets called
            if(file.shallowEquals(currentPlaylist.get().currentItem().file))
                this.setTotalTrackCount(1)
            else
                this.setTotalTrackCount(null)
        }.let {
            builder.setMediaMetadata(it.build())
        }
    }

    private fun mediaFilePeriods(file: MediaFile): List<PeriodData> {
        //TODO maybe use chapters from VLC (should be loaded for current file)
        if(file.chapters.isNullOrEmpty()) {
            return PeriodData.Builder(file.entityId).apply {
                this.setDurationUs(file.mediaTags.length * 1000)
            }.let {
                listOf(it.build())
            }
        } else {
            return file.chapters!!.map { chapter ->
                val id = Objects.hash(file, chapter)
                PeriodData.Builder(id).apply {
                    this.setDurationUs(chapter.end - chapter.start)
                }.build()
            }
        }
    }
    //endregion
    //endregion
}
