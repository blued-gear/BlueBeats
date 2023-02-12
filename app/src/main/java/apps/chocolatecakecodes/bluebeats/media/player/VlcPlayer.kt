package apps.chocolatecakecodes.bluebeats.media.player

import android.annotation.SuppressLint
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.media.playlist.UNDETERMINED_COUNT
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.util.RequireNotNull
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

//TODO create a wrapping session and use it in the app to control a VlcPlayer

@Suppress("NestedLambdaShadowedImplicitParameter")
@SuppressLint("RestrictedApi")
internal class VlcPlayer(libVlc: ILibVLC) : SessionPlayer(), MediaPlayer.EventListener {

    companion object {
        private const val LOG_TAG = "VlcPlayer"
    }

    private val player = MediaPlayer(libVlc)
    private val chapters = ArrayList<Chapter>()
    private var currentMedia = RequireNotNull<MediaFile>()
    private var currentPlaylist: PlaylistIterator? = null
    private var suppressCallbacks: Boolean = false
    private var newMediaLoading: Boolean = false

    init {
        player.setEventListener(this)
    }

    fun release() {
        stop()
        player.release()
    }

    fun setVideoOutput(out: VLCVideoLayout) {
        if(player.vlcVout.areViewsAttached())
            player.detachViews()
        player.attachViews(out, null, false, false)//TODO in future version subtitle option should be settable
    }

    fun clearVideoOutput() {
        player.detachViews()
    }

    //fun isOutputAttached() = player.vlcVout.areViewsAttached()

    fun playMedia(media: MediaFile) {
        //TODO do I have to stop playback (like in the old impl)
        playMedia(media, false)
    }

    fun playPlaylist(playlist: PlaylistIterator) {
        currentPlaylist = playlist

        callListeners {
            it.onPlaylistChanged(this, emptyList(), null)//XXX can't convert PlaylistIterator to list of items
            it.onRepeatModeChanged(this, repeatMode)
            it.onShuffleModeChanged(this, shuffleMode)

            if(it is PlayerCallback) {
                it.onPlaylistChanged(this)
            }
        }

        playMedia(playlist.nextMedia(), true)
    }

    fun stop() {
        player.stop()

        clearPlaylist()

        currentMedia.set(null)
        callListeners {
            it.onCurrentMediaItemChanged(this, null)
        }
    }

    fun isPlaying() = player.isPlaying

    fun getCurrentMedia() = if(currentMedia.isNull()) null else currentMedia.get()

    fun getCurrentPlaylist() = currentPlaylist

    fun getChapters(): List<Chapter> = Collections.unmodifiableList(chapters)

    fun seekPlaylist(by: Int): ListenableFuture<PlayerResult> {
        return currentPlaylist?.let {
            try {
                it.seek(by)
            }catch (e: IllegalArgumentException) {
                return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
            }

            playMedia(it.currentMedia(), true)

            immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
        } ?: immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
    }

    //region interface methods
    override fun play(): ListenableFuture<PlayerResult> {
        if(currentMedia.isNull()){
            return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
        }

        player.play()
        return immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
    }

    override fun pause(): ListenableFuture<PlayerResult> {
        if(currentMedia.isNull()){
            return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
        }

        player.pause()
        return immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
    }

    override fun prepare(): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
    }

    override fun seekTo(position: Long): ListenableFuture<PlayerResult> {
        if(currentMedia.isNull()){
            return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
        }

        player.setTime(position.coerceAtLeast(0).coerceAtMost(player.length), false)

        callListeners {
            it.onSeekCompleted(this, position)
        }
        return immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
    }

    override fun setPlaybackSpeed(playbackSpeed: Float): ListenableFuture<PlayerResult> {
        player.rate = playbackSpeed

        callListeners {
            it.onPlaybackSpeedChanged(this, player.rate)
        }
        return immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
    }

    override fun setAudioAttributes(attributes: AudioAttributesCompat): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun getPlayerState(): Int {
        if(currentMedia.isNull())
            return PLAYER_STATE_IDLE
        return if(player.isPlaying)
            PLAYER_STATE_PLAYING
        else
            PLAYER_STATE_PAUSED
    }

    override fun getCurrentPosition(): Long {
        if(currentMedia.isNull()){
            return UNKNOWN_TIME
        }

        return player.time
    }

    override fun getDuration(): Long {
        if(currentMedia.isNull()){
            return UNKNOWN_TIME
        }

        return if(player.length < 0)
            UNKNOWN_TIME
        else
            player.length
    }

    override fun getBufferedPosition(): Long {
        return UNKNOWN_TIME
    }

    override fun getBufferingState(): Int {
        return BUFFERING_STATE_UNKNOWN
    }

    override fun getPlaybackSpeed(): Float {
        return player.rate
    }

    override fun setPlaylist(list: MutableList<MediaItem>, metadata: MediaMetadata?): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun getAudioAttributes(): AudioAttributesCompat? {
        return AudioAttributesCompat.Builder().apply {
            setUsage(AudioAttributesCompat.USAGE_MEDIA)
            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
        }.build()
    }

    override fun setMediaItem(item: MediaItem): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun addPlaylistItem(index: Int, item: MediaItem): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun removePlaylistItem(index: Int): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun replacePlaylistItem(index: Int, item: MediaItem): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun skipToPreviousPlaylistItem(): ListenableFuture<PlayerResult> {
        return seekPlaylist(-1)
    }

    override fun skipToNextPlaylistItem(): ListenableFuture<PlayerResult> {
        return seekPlaylist(1)
    }

    override fun skipToPlaylistItem(index: Int): ListenableFuture<PlayerResult> {
        return currentPlaylist?.let {
            seekPlaylist(index - it.currentPosition)
        } ?: immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
    }

    override fun updatePlaylistMetadata(metadata: MediaMetadata?): ListenableFuture<PlayerResult> {
        return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))
    }

    override fun setRepeatMode(repeatMode: Int): ListenableFuture<PlayerResult> {
        if(repeatMode == REPEAT_MODE_GROUP || repeatMode == REPEAT_MODE_ONE)
            return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))

        return currentPlaylist?.let {
            val oldMode = it.repeat
            it.repeat = repeatMode != REPEAT_MODE_NONE

            if(oldMode != it.repeat) {
                callListeners {
                    it.onRepeatModeChanged(this, repeatMode)
                }

                immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
            } else {
                immediateFuture(PlayerResult(PlayerResult.RESULT_INFO_SKIPPED, currentMediaToMediaItem()))
            }
        } ?: immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
    }

    override fun setShuffleMode(shuffleMode: Int): ListenableFuture<PlayerResult> {
        if(shuffleMode == SHUFFLE_MODE_GROUP)
            return immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_NOT_SUPPORTED, null))

        return currentPlaylist?.let {
            val oldMode = it.shuffle
            it.shuffle = shuffleMode != SHUFFLE_MODE_NONE

            if(oldMode != it.shuffle) {
                callListeners {
                    it.onShuffleModeChanged(this, shuffleMode)
                }

                immediateFuture(PlayerResult(PlayerResult.RESULT_SUCCESS, currentMediaToMediaItem()))
            } else {
                immediateFuture(PlayerResult(PlayerResult.RESULT_INFO_SKIPPED, currentMediaToMediaItem()))
            }
        } ?: immediateFuture(PlayerResult(PlayerResult.RESULT_ERROR_INVALID_STATE, null))
    }

    override fun getPlaylist(): MutableList<MediaItem>? {
        return currentPlaylist?.let {
            it.getItems().map {
                mediaFileToMediaItem(it)
            }.toMutableList()
        }
    }

    override fun getPlaylistMetadata(): MediaMetadata? {
        return null
    }

    override fun getRepeatMode(): Int {
        return currentPlaylist?.let {
            if(it.repeat)
                REPEAT_MODE_ALL
            else
                REPEAT_MODE_NONE
        } ?: REPEAT_MODE_NONE
    }

    override fun getShuffleMode(): Int {
        return currentPlaylist?.let {
            if(it.shuffle)
                SHUFFLE_MODE_ALL
            else
                SHUFFLE_MODE_NONE
        } ?: SHUFFLE_MODE_NONE
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return currentMediaToMediaItem()
    }

    override fun getCurrentMediaItemIndex(): Int {
        return currentPlaylist?.let {
            if(it.currentPosition == UNDETERMINED_COUNT)
                return INVALID_ITEM_INDEX
            return it.currentPosition
        } ?: INVALID_ITEM_INDEX
    }

    override fun getPreviousMediaItemIndex(): Int {
        return currentPlaylist?.let {
            if(it.currentPosition == UNDETERMINED_COUNT)
                return INVALID_ITEM_INDEX
            if(it.currentPosition == 0)
                return INVALID_ITEM_INDEX
            return it.currentPosition - 1
        } ?: INVALID_ITEM_INDEX
    }

    override fun getNextMediaItemIndex(): Int {
        return currentPlaylist?.let {
            if(it.currentPosition == UNDETERMINED_COUNT)
                return INVALID_ITEM_INDEX
            if(it.currentPosition == it.totalItems - 1)
                return INVALID_ITEM_INDEX
            return it.currentPosition + 1
        } ?: INVALID_ITEM_INDEX
    }
    //endregion

    //region player callbacks
    override fun onEvent(event: MediaPlayer.Event?) {
        event?.let {
            when(it.type) {
                MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering,
                    MediaPlayer.Event.Playing, MediaPlayer.Event.Paused,
                    MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> onPlayingChanged(it.type)
                MediaPlayer.Event.MediaChanged -> onMediaChanged()
                MediaPlayer.Event.TimeChanged -> onTimeChanged()
                MediaPlayer.Event.EncounteredError -> onPlayerError()
            }
        }
    }

    private fun onPlayingChanged(state: Int) {
        when(state) {
            MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> {
                callListeners {
                    it.onPlayerStateChanged(this, PLAYER_STATE_PAUSED)
                    it.onBufferingStateChanged(this, currentMediaToMediaItem(), BUFFERING_STATE_BUFFERING_AND_STARVED)
                }
            }
            MediaPlayer.Event.Paused -> {
                callListeners {
                    it.onPlayerStateChanged(this, PLAYER_STATE_PAUSED)
                }
            }
            MediaPlayer.Event.Playing -> {
                callListeners {
                    it.onPlayerStateChanged(this, PLAYER_STATE_PLAYING)
                }
                onPlaying()
            }
            MediaPlayer.Event.EndReached -> {
                callListeners {
                    it.onPlayerStateChanged(this, PLAYER_STATE_PAUSED)
                }
                onEndReached()
            }
            MediaPlayer.Event.Stopped -> {
                callListeners {
                    it.onPlayerStateChanged(this, PLAYER_STATE_IDLE)
                }
            }
        }
    }

    private fun onPlaying() {
        if(newMediaLoading){
            newMediaLoading = false

            // chapter-info should now be loaded
            loadChapters()
        }
    }

    private fun onMediaChanged() {
        newMediaLoading = true

        callListeners {
            it.onCurrentMediaItemChanged(this, currentMediaToMediaItem())
        }
    }

    private fun onEndReached() {
        val pl = currentPlaylist
        if(pl !== null) {
            if(!pl.isAtEnd()) {
                playNextPlaylistItem()
            } else {
                onTotalEndReached()
            }
        } else {
            onTotalEndReached()
        }
    }

    private fun onTotalEndReached() {
        // reset player, or else seek will break
        suppressCallbacks = true
        player.play(currentMedia.get().path)
        player.pause()
        suppressCallbacks = false

        callListeners {
            it.onPlaybackCompleted(this)
        }
    }

    private fun playNextPlaylistItem() {
        CoroutineScope(Dispatchers.IO).launch {
            playMedia(currentPlaylist!!.nextMedia(), true)
        }
    }

    private fun onTimeChanged() {
        callListeners {
            if(it is PlayerCallback) {
                it.onTimeChanged(this, player.time)
            }
        }
    }

    private fun onPlayerError() {
        Log.e(LOG_TAG, "received error-event")
        callListeners {
            it.onPlayerStateChanged(this, PLAYER_STATE_ERROR)
        }
    }
    //endregion

    //region private methods
    private fun currentMediaToMediaItem(): MediaItem? {
        return if(currentMedia.isNull())
            null
        else
            mediaFileToMediaItem(currentMedia.get())
    }

    private fun mediaFileToMediaItem(media: MediaFile): MediaItem {
        return MediaItem.Builder().apply {
            this.setStartPosition(0)
            this.setEndPosition(media.mediaTags.length)
            MediaMetadata.Builder().apply {
                this.putString(MediaMetadata.METADATA_KEY_TITLE, media.title)
                this.putLong(MediaMetadata.METADATA_KEY_DURATION, media.mediaTags.length)
                if(!media.mediaTags.artist.isNullOrEmpty())
                    this.putString(MediaMetadata.METADATA_KEY_ARTIST, media.mediaTags.artist)
                if(!media.mediaTags.genre.isNullOrEmpty())
                    this.putString(MediaMetadata.METADATA_KEY_GENRE, media.mediaTags.genre)
            }.let {
                this.setMetadata(it.build())
            }
        }.build()
    }

    private fun <T> immediateFuture(value: T) = SettableFuture.create<T>().apply {
        set(value)
    }

    private fun callListeners(call: (SessionPlayer.PlayerCallback) -> Unit) {
        if(suppressCallbacks) return

        this.callbacks.forEach {
            it.second.execute {
                call(it.first)
            }
        }
    }

    private fun loadChapters() {
        // 1.: use chapters from MediaFile; 2.: if not available use from player; 3.: leave empty
        val mediaChapters = currentMedia.get().chapters
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

        getChapters().let { chaptersRO ->
            callListeners {
                if(it is PlayerCallback) {
                    it.onChaptersChanged(this, chaptersRO)
                }
            }
        }
    }

    private fun playMedia(media: MediaFile, keepPlaylist: Boolean) {
        if(!keepPlaylist) {
            clearPlaylist()
        }

        chapters.clear()
        getChapters().let { chaptersRO ->
            callListeners {
                if(it is PlayerCallback) {
                    it.onChaptersChanged(this, chaptersRO)
                }
            }
        }

        currentMedia.set(media)
        player.play(media.path)
    }

    private fun clearPlaylist() {
        if(currentPlaylist !== null) {
            currentPlaylist = null

            callListeners {
                it.onPlaylistChanged(this, null, null)
                it.onRepeatModeChanged(this, repeatMode)
                it.onShuffleModeChanged(this, shuffleMode)

                if(it is PlayerCallback) {
                    it.onPlaylistChanged(this)
                }
            }
        }
    }
    //endregion

    //region classes
    abstract class PlayerCallback : SessionPlayer.PlayerCallback() {

        open fun onTimeChanged(player: VlcPlayer, time: Long){}

        open fun onChaptersChanged(player: VlcPlayer, chapters: List<Chapter>){}

        open fun onPlaylistChanged(player: VlcPlayer){}
    }
    //endregion
}