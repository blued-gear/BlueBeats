package apps.chocolatecakecodes.bluebeats.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class Player : Fragment() {

    companion object {
        fun newInstance() = Player()

        private const val SEEK_AREA_WIDTH = 0.4f// in %/100 (per side)
        private const val SEEK_AMOUNT = 5000L// in ms TODO make changeable by user
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup player-view
        playerView = VLCVideoLayout(this.requireContext())
        player.attachViews(playerView, null, false, false)//TODO in future version subtitle option should be settable
        view.findViewById<FrameLayout>(R.id.player_playerholder).addView(playerView)

        val gestureHandler = PlayerGestureHandler()
        val gestureDetector = GestureDetectorCompat(this.requireContext(), gestureHandler)
        gestureDetector.setOnDoubleTapListener(gestureHandler)
        gestureDetector.setIsLongpressEnabled(false)
        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        wireObservers()
        wireActionHandlers(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.updatePlayPosition(player.time)
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
            if(it !== null) {
                playMedia(it)
            }
        }

        viewModel.isPlaying.observe(this.viewLifecycleOwner){
            if(it !== null) {
                if (it)
                    player.play()
                else
                    player.pause()
            }
        }

        viewModel.playPos.observe(this.viewLifecycleOwner){
            if(it !== null){
                player.time = it
            }
        }
    }

    private fun playMedia(mediaFile: MediaFile){
        player.stop()

        val newMedia = VlcManagers.getMediaDB().getSubject().fileToVlcMedia(mediaFile.path)
        if(newMedia === null)
            throw IllegalArgumentException("can not create media from file")

        if(currentMedia !== null)
            currentMedia!!.release()

        this.currentMedia = newMedia

        if(!player.vlcVout.areViewsAttached())//XXX if audio is played vlc will detach teh playerView
            player.attachViews(playerView, null, false, false)

        player.play(newMedia)
    }

    private inner class PlayerGestureHandler : GestureDetector.SimpleOnGestureListener(){

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            playerView.performClick()

            if(viewModel.isPlaying.value == true)
                viewModel.pause()
            else if(viewModel.currentMedia.value !== null)
                viewModel.resume()

            return true
        }

        override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
            if(e === null) return false
            if(e.actionMasked != MotionEvent.ACTION_UP) return false
            if(viewModel.currentMedia.value === null) return false

            val width = playerView.width
            val x = e.x

            if(x <= width * SEEK_AREA_WIDTH){
                // seek back
                viewModel.updatePlayPosition((player.time - SEEK_AMOUNT).coerceAtLeast(0))

                return true
            }else if(x >= width * (1.0f - SEEK_AREA_WIDTH)){
                // seek forward
                viewModel.updatePlayPosition((player.time + SEEK_AMOUNT).coerceAtMost(player.length))

                return true
            }

            return false
        }
    }
}