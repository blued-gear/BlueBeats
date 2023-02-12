package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import com.anggrayudi.storage.extension.launchOnUiThread
import kotlinx.coroutines.delay
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.lang.IllegalStateException

/**
 * manages a player
 */
class VlcPlayerManager(context: Context, private val mediaDB: MediaDB) {

    private val player: MediaPlayer
    private val playerView: VLCVideoLayout
    private var currentPlayerViewHolder: FrameLayout? = null;

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
    }

    fun isPlayerAttached(): Boolean{
        return currentPlayerViewHolder !== null
    }

    fun play(media: MediaFile){
        player.play(Media(libVlc, media.path))
    }
}