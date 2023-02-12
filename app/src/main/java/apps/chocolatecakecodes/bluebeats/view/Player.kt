package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class Player : Fragment() {

    companion object {
        fun newInstance() = Player()
    }

    private lateinit var viewModel: PlayerViewModel
    private lateinit var player: MediaPlayer
    private lateinit var playerView: VLCVideoLayout
    private var currentMedia: IMedia? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this.requireActivity()).get(PlayerViewModel::class.java)
        player = MediaPlayer(VlcManagers.getLibVlc())
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        if(currentMedia !== null)
            currentMedia!!.release()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.player_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup player-view
        playerView = VLCVideoLayout(this.requireContext())
        player.attachViews(playerView, null, false, false)//TODO in future version subtitle option should be settable
        view.findViewById<FrameLayout>(R.id.player_playerholder).addView(playerView)

        wireObservers()
        wireActionHandlers(view)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // resume play if media is loaded
        if(viewModel.currentMedia.value !== null){
            playMedia(viewModel.currentMedia.value!!)
            val playPos = viewModel.playPos.value
            if(playPos !== null)
            player.position = playPos
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.updatePlayPosition(player.position)
    }

    private fun wireActionHandlers(view: View){
        view.findViewById<Button>(R.id.player_btn_resume).setOnClickListener {
            viewModel.resume()
        }
        view.findViewById<Button>(R.id.player_btn_pause).setOnClickListener {
            viewModel.pause()
        }
    }

    private fun wireObservers(){
        viewModel.currentMedia.observe(this.viewLifecycleOwner){
            playMedia(it)
        }

        viewModel.isPlaying.observe(this.viewLifecycleOwner){
            if(it)
                player.play()
            else
                player.pause()
        }
    }

    private fun playMedia(mediaFile: MediaFile){
        val newMedia = VlcManagers.getMediaDB().getSubject().fileToVlcMedia(mediaFile.path)
        if(newMedia === null)
            throw IllegalArgumentException("can not create media from file")

        if(currentMedia !== null)
            currentMedia!!.release()

        this.currentMedia = newMedia
        player.play(newMedia)
    }
}