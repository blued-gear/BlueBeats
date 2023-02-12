package apps.chocolatecakecodes.bluebeats.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.*
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

private const val CONTROLS_FADE_IN_TIME = 200L
private const val CONTROLS_FADE_OUT_TIME = 100L
private const val CONTROLS_FADE_OUT_DELAY = 2000L
private const val SEEK_STEP = 1000.0f

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
    private var seekBar: SegmentedSeekBar by OnceSettable()
    private var timeTextView: TextView by OnceSettable()
    private var mainMenu: Menu? = null
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
        timeTextView = view.findViewById(R.id.player_controls_time)

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
        setupMainMenu()
    }

    override fun onPause() {
        super.onPause()

        mainMenu = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.updatePlayPosition(player.time)
    }

    //region action-handlers
    @SuppressLint("ClickableViewAccessibility")
    private fun wireActionHandlers(view: View){
        // player control by tapping
        val controlsPane = view.findViewById<ViewGroup>(R.id.player_controls_overlay)
        setupControlPaneGestures(controlsPane)

        // play/pause button
        view.findViewById<View>(R.id.player_controls_play).setOnClickListener {
            onPlayPauseClick()
        }

        // fullscreen button
        view.findViewById<View>(R.id.player_controls_fullscreen).setOnClickListener {
            onFullscreenClick()
        }

        // fullscreen close on back
        mainVM.addBackPressListener(this.viewLifecycleOwner){
            // check fullscreen is active
            if(mainVM.fullScreenContent.value !== null){
                viewModel.setFullscreenMode(false)
            }
        }

        // seek-bar
        seekBar.seekListener = seekHandler

        // toggle viewModel.timeTextAsRemaining on click at timeTextView
        timeTextView.setOnClickListener {
            viewModel.setTimeTextAsRemaining(!viewModel.timeTextAsRemaining.value!!)
        }

        // player-event-listener
        player.setEventListener(PlayerEventHandler())
    }

    private fun setupControlPaneGestures(controlsPane: View) {
        val gestureHandler = ControlsGestureHandler(controlsPane)

        val gestureDetector = GestureDetectorCompat(this.requireContext(), gestureHandler)
        gestureDetector.setOnDoubleTapListener(gestureHandler)
        gestureDetector.setIsLongpressEnabled(false)

        controlsPane.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private fun onPlayPauseClick() {
        if(viewModel.isPlaying.value == true)
            viewModel.pause()
        else if(viewModel.currentMedia.value !== null)
            viewModel.resume()
    }

    private fun onFullscreenClick() {
        viewModel.setFullscreenMode(!(viewModel.isFullscreen.value ?: false))
    }
    //endregion

    //region livedata-handlers
    private fun wireObservers(){
        viewModel.currentMedia.observe(this.viewLifecycleOwner){
            if(it !== null) {
                playMedia(it)
            }
        }

        viewModel.isPlaying.observe(this.viewLifecycleOwner){
            onIsPlayingChanged(it)
        }

        viewModel.playPos.observe(this.viewLifecycleOwner){
            onPlayPosChanged(it)
        }

        viewModel.isFullscreen.observe(this.viewLifecycleOwner){
            onIsFullscreenChanged(it)
        }

        viewModel.timeTextAsRemaining.observe(this.viewLifecycleOwner){
            timeTextView.text = formatPlayTime(player.time, player.length, it!!)
        }

        viewModel.chapters.observe(this.viewLifecycleOwner){
            onChaptersChanged(it)
        }

        viewModel.currentPlaylist.observe(this.viewLifecycleOwner) {
            updatePlaylistMenuItem()
        }
    }

    private fun onIsPlayingChanged(value: Boolean?) {
        if(value !== null) {
            if (value) {
                player.play()

                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
            }else {
                player.pause()

                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
            }

            if (value){
                hideControlsWithDelay()
            }
        }
    }

    private fun onPlayPosChanged(value: Long?) {
        if((value !== null)){
            if(abs(value - player.time) > 5)// threshold of 5ms to prevent cyclic calls from this and PlayerEventHandler
                if(seekHandler.isSeeking)
                    player.setTime(value, true)
                else
                    player.setTime(value, false)

            if(!seekHandler.isSeeking) {
                seekBar.value = ((value / player.length.toDouble()) * SEEK_STEP).toInt().coerceAtLeast(1)
            }

            timeTextView.text = formatPlayTime(value, player.length, viewModel.timeTextAsRemaining.value!!)
        }
    }

    private fun onIsFullscreenChanged(value: Boolean?) {
        if(value !== null){
            showFullscreen(value)

            if(value)
                playerContainer.findViewById<ImageButton>(R.id.player_controls_fullscreen).setImageResource(R.drawable.ic_baseline_fullscreen_exit)
            else
                playerContainer.findViewById<ImageButton>(R.id.player_controls_fullscreen).setImageResource(R.drawable.ic_baseline_fullscreen)
        }
    }

    private fun onChaptersChanged(value: List<Chapter>?) {
        if(value === null || value.isEmpty()){
            seekBar.segments = emptyArray()
            seekBar.showTitle = false
        }else{
            val totalTime = player.length.toDouble()

            // if fragment was recreated, this will be triggered before player was fully loaded (then totalTime == -1)
            if(totalTime > 0) {
                val segments = Array<SegmentedSeekBar.Segment>(value.size) {
                    val chapter = value[it]
                    val start = ((chapter.start.toDouble() / totalTime) * SEEK_STEP).toInt()
                    val end = ((chapter.end.toDouble() / totalTime) * SEEK_STEP).toInt()
                    return@Array SegmentedSeekBar.Segment(start, end, chapter.name)
                }

                seekBar.segments = segments
                seekBar.showTitle = true
            }
        }
    }
    //endregion

    //region menu
    private fun setupMainMenu(){
        mainVM.menuProvider.value = { menu, menuInflater ->
            menuInflater.inflate(R.menu.player_menu, menu)

            mainMenu = menu

            // configure items
            menu.findItem(R.id.player_menu_chapters).setOnMenuItemClickListener {
                showChapterMenu()
                return@setOnMenuItemClickListener true
            }
            menu.findItem(R.id.player_menu_playlist).setOnMenuItemClickListener {
                showPlaylistOverview()
                return@setOnMenuItemClickListener true
            }

            updateChaptersMenuItem()
            updatePlaylistMenuItem()
        }
    }

    private fun updateChaptersMenuItem(){
        mainMenu?.let {
            val chaptersItem = it.findItem(R.id.player_menu_chapters)
            val chapters = viewModel.chapters.value
            if(chapters === null || chapters.isEmpty())
                chaptersItem.isEnabled = false
        }
    }

    private fun showChapterMenu(){
        val dlgBuilder = AlertDialog.Builder(this.requireContext())

        // generate chapter items
        val chapters = viewModel.chapters.value!!
        val itemTexts = chapters.map {
            val start = Utils.formatTime(it.start)
            val end = Utils.formatTime(it.end)
            return@map "${it.name} ($start - $end)"
        }.toTypedArray()

        dlgBuilder.setItems(itemTexts){ _, itemIdx ->
            // jump to chapter
            val chapter = chapters[itemIdx]
            viewModel.updatePlayPosition(chapter.start)
        }

        dlgBuilder.create().show()
    }

    private fun updatePlaylistMenuItem() {
        mainMenu?.let {
            it.findItem(R.id.player_menu_playlist).isEnabled = viewModel.currentPlaylist.value != null
        }
    }

    private fun showPlaylistOverview() {
        Utils.showPopup(this.requireContext(), this.requireView(),
            R.layout.player_playlist_fragment,
        true) {

        }
    }
    //endregion

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
                    hideControlsWithDelay()
                }
            }
            .start()
    }

    private fun hideControlsWithDelay() {
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

    private fun playMedia(mediaFile: MediaFile){
        player.stop()

        val newMedia = VlcManagers.getMediaDB().getSubject().fileToVlcMedia(mediaFile.path)
        if(newMedia === null)
            throw IllegalArgumentException("can not create media from file")

        if(currentMedia !== null)
            currentMedia!!.release()

        this.currentMedia = newMedia

        attachPlayer()

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

    private fun formatPlayTime(time: Long, len: Long, remaining: Boolean): String{
        val timeCapped = time.coerceAtLeast(0)
        val lenCapped = len.coerceAtLeast(0)

        val withHours = (lenCapped / (60 * 60 * 1000)) > 0

        val timeStr =
            if(remaining) "-" + Utils.formatTime(lenCapped - timeCapped, withHours)
            else Utils.formatTime(timeCapped, withHours)
        val lenStr = Utils.formatTime(lenCapped, withHours)

        return "$timeStr / $lenStr"
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

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if(isSeeking)
                viewModel.updatePlayPosition((player.length * (this@Player.seekBar.value / SEEK_STEP)).toLong())
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            isSeeking = true

            // cancel hiding of controls while seeking
            controlsHideCoroutine?.cancel(null)
            controlsHideCoroutine = null
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            isSeeking = false

            // re-schedule control-hiding
            if(viewModel.isPlaying.value == true) {// only re-hide if playing
                hideControlsWithDelay()
            }
        }
    }

    private inner class PlayerEventHandler : MediaPlayer.EventListener{

        private var newMediaLoading = false

        override fun onEvent(event: MediaPlayer.Event?) {
            if(event === null) return

            when(event.type){
                MediaPlayer.Event.TimeChanged -> onTimeChanged()
                MediaPlayer.Event.EndReached -> onEndReached()
                MediaPlayer.Event.MediaChanged -> onMediaChanged()
                MediaPlayer.Event.Playing -> onPlaying()
            }
        }

        private fun onTimeChanged() {
            viewModel.updatePlayPosition(player.time)
        }

        private fun onEndReached() {
            val pl = viewModel.currentPlaylist.value
            if(pl !== null) {
                if(!pl.isAtEnd()) {
                    viewModel.play(pl.nextMedia())
                } else {
                    onTotalEndReached()
                }
            } else {
                onTotalEndReached()
            }
        }

        private fun onMediaChanged() {
            newMediaLoading = true
        }

        private fun onPlaying() {
            if(newMediaLoading){
                newMediaLoading = false

                // chapter-info should now be loaded
                // 1.: use chapters from MediaFile; 2.: if not available use from player; 3.: leve empty
                val chapters: List<Chapter>
                val mediaChapters = viewModel.currentMedia.value!!.chapters
                if(!mediaChapters.isNullOrEmpty()){
                    chapters = mediaChapters
                }else{
                    val vlcChapters: Array<MediaPlayer.Chapter>? = player.getChapters(-1)
                    if(vlcChapters !== null){
                        chapters = vlcChapters.map {
                            Chapter(it.timeOffset, it.timeOffset + it.duration, it.name)
                        }
                    }else{
                        chapters = emptyList()
                    }
                }
                viewModel.setChapters(chapters)
            }
        }

        private fun onTotalEndReached() {
            // reset player, or else seek will break
            player.play(currentMedia!!)
            viewModel.updatePlayPosition(0)
            viewModel.pause()
        }
    }
}