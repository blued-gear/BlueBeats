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
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.BasicPlayer
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.Chapter
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.TempPlaylist
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.TimeSpanItem
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
import java.util.Collections
import java.util.Objects
import kotlin.concurrent.Volatile

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VlcPlayer(libVlc: ILibVLC, looper: Looper) : SimpleBasePlayer(looper), BasicPlayer, MediaPlayer.EventListener {

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
    fun playMedia(media: MediaFile) {
        playMedia(media, false)
    }

    override fun playMedia(media: MediaFile, keepPlaylist: Boolean) {
        if(!keepPlaylist) {
            playPlaylist(TempPlaylist(media))
        } else {
            chapters.clear()

            playbackPos = 0
            player.play(media.path)
        }

        // state will be invalidated by VLC event

        // this will be set at state-rebuild on VLC event, but it may be needed earlier so also set it now
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(state) {
                fillInStateMedia(state)
                mainThreadHandler.post { invalidateState() }
            }
        }
    }

    fun playPlaylist(playlist: PlaylistIterator) {
        currentPlaylist.getNullable()?.let { pl ->
            if(pl is TempPlaylist) {
                pl.removeChangeListener(this::invalidateMediaInfo)
            }
        }

        currentPlaylist.set(playlist)

        if(playlist is TempPlaylist) {
            playlist.addChangeListener(this::invalidateMediaInfo)
        }

        synchronized(state) {
            when(playlist.repeat) {
                PlaylistIterator.RepeatMode.NONE -> state.setRepeatMode(Player.REPEAT_MODE_OFF)
                PlaylistIterator.RepeatMode.ALL -> state.setRepeatMode(Player.REPEAT_MODE_ALL)
                PlaylistIterator.RepeatMode.ONE -> state.setRepeatMode(Player.REPEAT_MODE_ONE)
            }
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

        currentPlaylist.getNullable()?.let { pl ->
            if(pl is TempPlaylist) {
                pl.removeChangeListener(this::invalidateMediaInfo)
            }
        }

        currentPlaylist.set(null)
        playbackPos = 0

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

                if(pl.currentPosition == PlaylistIterator.UNDETERMINED_COUNT) {
                    ret.setException(IllegalStateException("playlist not started"))
                    return@launch
                }

                if(mediaItemIndex != pl.currentPosition) {
                    if(seekCommand == Player.COMMAND_SEEK_TO_NEXT || seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                        pl.nextItem()
                    } else {
                        pl.seek(mediaItemIndex - pl.currentPosition)
                    }

                    pl.currentItem().play(this@VlcPlayer)
                }

                if(positionMs != C.TIME_UNSET) {
                    //TODO this might not work as the player might still be loading
                    player.setTime(positionMs, false)
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
        val pl = currentPlaylist.get()
        when(repeatMode) {
            Player.REPEAT_MODE_OFF -> pl.repeat = PlaylistIterator.RepeatMode.NONE
            Player.REPEAT_MODE_ALL -> pl.repeat = PlaylistIterator.RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> pl.repeat = PlaylistIterator.RepeatMode.ONE
        }

        // DynamicPlaylist may change the set value as NONE is forbidden in this case
        when(pl.repeat) {
            PlaylistIterator.RepeatMode.NONE -> state.setRepeatMode(Player.REPEAT_MODE_OFF)
            PlaylistIterator.RepeatMode.ALL -> state.setRepeatMode(Player.REPEAT_MODE_ALL)
            PlaylistIterator.RepeatMode.ONE -> state.setRepeatMode(Player.REPEAT_MODE_ONE)
        }

        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        // on DynamicPlaylist this will trigger regeneration, so run it outside of main-thread
        val ret = SettableFuture.create<Unit>()
        CoroutineScope(Dispatchers.IO).launch {
            currentPlaylist.get().shuffle = shuffleModeEnabled

            synchronized(state) {
                // re-create playlist info as shuffle might have changed the order
                fillInStateMedia(state)
            }

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
        // nextItem() could trigger DB actions
        CoroutineScope(Dispatchers.IO).launch {
            currentPlaylist.get().nextItem().play(this@VlcPlayer)
        }
    }

    private fun invalidateMediaInfo() {
        // in IO-thread because fillInStateMedia() will load media-attrs from DB
        CoroutineScope(Dispatchers.IO).launch {
            loadChapters()
            synchronized(state) {
                fillInStateMedia(state)
                mainThreadHandler.post { invalidateState() }
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
        builder.setMediaId(file.id.toString())
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

            // as player-specific data (like chapters) may have changed, force a onMediaMetadataChanged() event every time this metadata is rebuild
            // this misuses the station field, but *shrug*
            this.setStation(System.currentTimeMillis().toString())
        }.let {
            builder.setMediaMetadata(it.build())
        }
    }

    private fun mediaFilePeriods(file: MediaFile): List<PeriodData> {
        //TODO maybe use chapters from VLC (should be loaded for current file)
        return if(file.chapters.isNullOrEmpty()) {
            PeriodData.Builder(file.id).apply {
                this.setDurationUs(file.mediaTags.length * 1000)
            }.let {
                listOf(it.build())
            }
        } else {
            file.chapters!!.map { chapter ->
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
