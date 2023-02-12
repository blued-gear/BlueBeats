package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import android.widget.FrameLayout
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.*
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.IllegalStateException

/**
 * manages a player
 */
class VlcPlayerManager(context: Context, private val mediaDB: MediaDB) {

    private val player: MediaPlayer
    private val playerView: VLCVideoLayout
    private var currentPlayerViewHolder: FrameLayout? = null
    private var currentMedia: IMedia? = null

    init{
        player = MediaPlayer(VlcManagers.getLibVlc())
        playerView = VLCVideoLayout(context)

        player.attachViews(playerView, null, false, false)//TODO in future version subtitle option should be settable
    }

    fun attachPlayer(parent: FrameLayout){
        if(currentPlayerViewHolder != null)
            throw IllegalStateException("playerView is already attached")
        parent.addView(playerView)
        currentPlayerViewHolder = parent;
    }

    fun detachPlayer(){
        if(isPlayerAttached())
            throw IllegalStateException("playerView is not attached")

        player.stop()

        currentPlayerViewHolder!!.removeView(playerView)
        currentPlayerViewHolder = null

        val media = currentMedia
        if(media !== null){
            media.release()
            currentMedia = null
        }
    }

    fun isPlayerAttached(): Boolean{
        return currentPlayerViewHolder !== null
    }

    fun play(mediaFile: MediaFile){
        if(!isPlayerAttached())
            throw IllegalStateException("player is not attached to a view")

        val media = currentMedia
        if(media !== null){
            media.release()
            currentMedia = null
        }

        val currentMedia = mediaDB.fileToVlcMedia(mediaFile.path)
        if(currentMedia === null)
            throw IllegalArgumentException("file could not be converted to media")
        currentMedia.retain()
        this.currentMedia = currentMedia

        player.play(currentMedia)
    }
}