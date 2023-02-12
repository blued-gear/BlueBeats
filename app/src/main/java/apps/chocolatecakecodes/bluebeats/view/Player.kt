package apps.chocolatecakecodes.bluebeats.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import kotlinx.coroutines.*
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

private const val CONTROLS_FADE_IN_TIME = 200L
private const val CONTROLS_FADE_OUT_TIME = 100L
private const val CONTROLS_FADE_OUT_DELAY = 2000L
private const val SEEK_STEP = 1000.0

class Player : Fragment() {

    companion object {
        fun newInstance() = Player()

        private const val SEEK_AREA_WIDTH = 0.4f// in %/100 (per side)
        private const val SEEK_AMOUNT = 5000L// in ms TODO make changeable by user
    }

    private var viewModel: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var player: MediaPlayer by OnceSettable()
    private var playerView: VLCVideoLayout by OnceSettable()
    private var playerContainer: ViewGroup by OnceSettable()
    private var seekBar: SeekBar by OnceSettable()
    private var currentMedia: IMedia? = null
    private var controlsVisible: Boolean = true
    private var controlsHideCoroutine: Job? = null
    private val seekHandler = SeekHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

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

        playerContainer = view.findViewById(R.id.player_player_container)
        seekBar = view.findViewById(R.id.player_controls_seek)
        seekBar.max = SEEK_STEP.toInt()

        // setup player-view
        playerView = VLCVideoLayout(this.requireContext())
        view.findViewById<FrameLayout>(R.id.player_playerholder).addView(playerView)
        attachPlayer()

        wireObservers()
        wireActionHandlers(view)
    }

    override fun onResume() {
        super.onResume()
        attachPlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.updatePlayPosition(player.time)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireActionHandlers(view: View){
        // player control by tapping
        val controlsPane = view.findViewById<ViewGroup>(R.id.player_controls_overlay)
        val gestureHandler = ControlsGestureHandler(controlsPane)
        val gestureDetector = GestureDetectorCompat(this.requireContext(), gestureHandler)
        gestureDetector.setOnDoubleTapListener(gestureHandler)
        gestureDetector.setIsLongpressEnabled(false)
        controlsPane.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        // play/pause button
        view.findViewById<View>(R.id.player_controls_play).setOnClickListener {
            if(viewModel.isPlaying.value == true)
                viewModel.pause()
            else if(viewModel.currentMedia.value !== null)
                viewModel.resume()
        }

        // fullscreen button
        view.findViewById<View>(R.id.player_controls_fullscreen).setOnClickListener {
            viewModel.setFullscreenMode(!(viewModel.isFullscreen.value ?: false))
        }

        // fullscreen close on back
        mainVM.addBackPressListener(this.viewLifecycleOwner){
            // check fullscreen is active
            if(mainVM.fullScreenContent.value !== null){
                viewModel.setFullscreenMode(false)
            }
        }

        // seek-bar
        seekBar.setOnSeekBarChangeListener(seekHandler)

        player.setEventListener(PlayerEventHandler())
    }

    private fun wireObservers(){
        viewModel.currentMedia.observe(this.viewLifecycleOwner){
            if(it !== null) {
                playMedia(it)
            }
        }

        viewModel.isPlaying.observe(this.viewLifecycleOwner){
            if(it !== null) {
                if (it) {
                    player.play()

                    playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
                }else {
                    player.pause()

                    playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
                }

                if (it){
                    // hide controls
                    controlsHideCoroutine?.cancel(null)
                    controlsHideCoroutine = CoroutineScope(Dispatchers.Default).launch {
                        delay(CONTROLS_FADE_OUT_DELAY)
                        launch(Dispatchers.Main) {
                            if (viewModel.isPlaying.value == true) {// only re-hide if playing
                                runControlsTransition(false)
                            }

                            controlsHideCoroutine = null
                        }
                    }
                }
            }
        }

        viewModel.playPos.observe(this.viewLifecycleOwner){
            if((it !== null)){
                if(abs(it - player.time) > 5)// threshold of 5ms to prevent cyclic calls from this and PlayerEventHandler
                    player.time = it

                if(!seekHandler.isSeeking) {
                    seekBar.progress = ((it / player.length.toDouble()) * SEEK_STEP).toInt().coerceAtLeast(1)
                }
            }
        }

        viewModel.isFullscreen.observe(this.viewLifecycleOwner){
            if(it !== null){
                showFullscreen(it)

                if(it)
                    playerContainer.findViewById<ImageButton>(R.id.player_controls_fullscreen).setImageResource(R.drawable.ic_baseline_fullscreen_exit)
                else
                    playerContainer.findViewById<ImageButton>(R.id.player_controls_fullscreen).setImageResource(R.drawable.ic_baseline_fullscreen)
            }
        }
    }

    private fun attachPlayer(){
        if(!player.vlcVout.areViewsAttached()){
            player.attachViews(playerView, null, false, false)//TODO in future version subtitle option should be settable
        }
    }

    private fun runControlsTransition(fadeIn: Boolean){
        if(!(fadeIn xor controlsVisible))// do nothing if the desired state is already set
            return
        if(this.view === null) return// can be null if view got re-attached (fullscreen) or rebuild

        val rootView = playerContainer
        val controlsPane = rootView.findViewById<ViewGroup>(R.id.player_controls_overlay)
        if(controlsPane === null) return// can be null if view got re-attached (fullscreen) or rebuild

        controlsVisible = fadeIn

        controlsPane.animate()
            .setDuration(if(fadeIn) CONTROLS_FADE_IN_TIME else CONTROLS_FADE_OUT_TIME)
            .alpha(if(fadeIn) 1.0f else 0.0f)
            .setUpdateListener {
                if(it.animatedFraction == 1.0f){// is finished
                    controlsHideCoroutine?.cancel(null)
                    controlsHideCoroutine = CoroutineScope(Dispatchers.Default).launch {
                        delay(CONTROLS_FADE_OUT_DELAY)
                        launch(Dispatchers.Main) {
                            if(viewModel.isPlaying.value == true){// only re-hide if playing
                                runControlsTransition(false)
                            }

                            controlsHideCoroutine = null
                        }
                    }
                }
            }
            .start()
    }

    private fun playMedia(mediaFile: MediaFile){
        player.stop()

        val newMedia = VlcManagers.getMediaDB().getSubject().fileToVlcMedia(mediaFile.path)
        if(newMedia === null)
            throw IllegalArgumentException("can not create media from file")

        if(currentMedia !== null)
            currentMedia!!.release()

        this.currentMedia = newMedia

        if(!player.vlcVout.areViewsAttached())//XXX if audio is played vlc will detach the playerView
            player.attachViews(playerView, null, false, false)

        player.play(newMedia)

        // this will prevent from autoplay after rotate
        CoroutineScope(Dispatchers.Default).launch {
            delay(5)
            withContext(Dispatchers.Main){
                if(viewModel.isPlaying.value != true)
                    player.pause()
            }
        }
    }

    private fun showFullscreen(fullscreen: Boolean){
        if(fullscreen){
            val parent = playerContainer.parent as ViewGroup
            parent.removeView(playerContainer)
            mainVM.fullScreenContent.postValue(playerContainer)

            // wait until view is attached to re-attach player
            CoroutineScope(Dispatchers.Main).launch {
                var success = false
                for(i in 0..10){
                    delay(10)
                    if(playerContainer.parent !== null){
                        success = true
                        break
                    }
                }
                if(success){
                    attachPlayer()
                }else{
                    Log.e("Player", "could not re-attach playerView: not released by parent")
                }
            }
        }else{
            mainVM.fullScreenContent.postValue(null)

            // wait until view is attached to re-attach playerView
            CoroutineScope(Dispatchers.Main).launch {
                var success = false
                for(i in 0..10){
                    delay(10)
                    if(playerContainer.parent === null){
                        success = true
                        break
                    }
                }
                if(success){
                    this@Player.requireView().findViewById<FrameLayout>(R.id.player_player_container_container)
                        .addView(playerContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    attachPlayer()
                }else{
                    Log.e("Player", "could not re-attach playerView: not released by parent")
                }
            }
        }
    }

    private inner class ControlsGestureHandler(private val view: View) : GestureDetector.SimpleOnGestureListener(){

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            view.performClick()

            runControlsTransition(!controlsVisible)

            return true
        }

        override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
            if(e === null) return false
            if(e.actionMasked != MotionEvent.ACTION_UP) return false
            if(viewModel.currentMedia.value === null) return false

            val width = view.width
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

    private inner class SeekHandler : SeekBar.OnSeekBarChangeListener{

        var isSeeking = false

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            isSeeking = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            isSeeking = false
            viewModel.updatePlayPosition((player.length * (this@Player.seekBar.progress / SEEK_STEP)).toLong())
        }
    }

    private inner class PlayerEventHandler : MediaPlayer.EventListener{

        override fun onEvent(event: MediaPlayer.Event?) {
            if(event === null) return

            when(event.type){
                MediaPlayer.Event.TimeChanged -> {
                    viewModel.updatePlayPosition(player.time)
                }
                MediaPlayer.Event.EndReached -> {
                    // reset player, or else seek will break
                    player.play(currentMedia!!)
                    viewModel.updatePlayPosition(0)
                    viewModel.pause()
                }
            }
        }
    }
}