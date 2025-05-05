package apps.chocolatecakecodes.bluebeats.media.playlist

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.BasicPlayer
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.TimeSpanItem
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.TimeSpanItemPlayerController as ItemPlayController

@androidx.annotation.OptIn(UnstableApi::class)
internal class TimeSpanItemPlayerController: ItemPlayController, TimerThread.TaskRunnable, Player.Listener {

    private lateinit var item: TimeSpanItem
    private lateinit var player: VlcPlayer
    private var timerId: Int = -1
    private var fileLoaded = false
    private var timeSet = false
    private var justSought = false
    private var cancel = false

    override fun init(item: TimeSpanItem, player: BasicPlayer) {
        this.item = item
        this.player = player as VlcPlayer
    }

    override fun register(callback: () -> Unit) {
        timerId = TimerThread.INSTANCE.addInterval(100, this)
        player.addListener(this)

        callback()
    }

    override suspend fun unregister() {
        cancel = true
        withContext(Dispatchers.Main) { player.removeListener(this@TimeSpanItemPlayerController) }
    }

    override fun invoke(): Long {
        return runBlocking {
            val itemActive = item == player.getCurrentPlaylist()?.currentItem()// check is necessary as onMediaMetadataChanged() might be called to late
            if (fileLoaded && itemActive) {
                checkTime()
            }

            if (cancel) {
                unregister()
                return@runBlocking -1L
            }

            return@runBlocking 0L
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        val currentPlItem = player.getCurrentPlaylist()?.currentItem()
        val currentFile = player.getCurrentMedia()

        if(fileLoaded) {
            if(item != currentPlItem || !item.file.shallowEquals(currentFile)) {
                fileLoaded = false
                cancel = true
            }
        } else {
            if(item.file.shallowEquals(currentFile)) {
                fileLoaded = true
            }
        }
    }

    private suspend fun checkTime() {
        withContext(Dispatchers.Main) {
            val time = player.currentPosition

            if(timeSet) {
                if(justSought) {
                    // wait till player did seek
                    if((time - item.startMs) < 2000)
                        justSought = false
                } else {
                    if(time >= item.endMs) {
                        player.seekToNext()
                        cancel = true
                    } else if(time < item.startMs - 1000) {
                        // the user seems to seeked on their own so stop control of this item
                        cancel = true
                    }
                }
            } else {
                player.seekTo(item.startMs)
                timeSet = true
                justSought = true
            }
        }
    }
}
