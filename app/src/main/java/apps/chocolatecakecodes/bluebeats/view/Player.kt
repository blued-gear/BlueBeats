package apps.chocolatecakecodes.bluebeats.view

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media2.common.MediaItem
import androidx.media2.common.SessionPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.service.PlayerService
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.SmartBackPressedCallback
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.castTo
import apps.chocolatecakecodes.bluebeats.view.specialitems.MediaFileItem
import apps.chocolatecakecodes.bluebeats.view.specialviews.SegmentedSeekBar
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.*
import org.videolan.libvlc.util.VLCVideoLayout

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

    private val playerCallback = PlayerListener()
    private var viewModel: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var player: VlcPlayer by OnceSettable()
    private var playerView: VLCVideoLayout by OnceSettable()
    private var playerContainer: ViewGroup by OnceSettable()
    private var seekBar: SegmentedSeekBar by OnceSettable()
    private var timeTextView: TextView by OnceSettable()
    private var altImgView: ImageView by OnceSettable()
    private var mainMenu: Menu? = null
    private var controlsVisible: Boolean = true
    private var controlsHideCoroutine: Job? = null
    private val seekHandler = SeekHandler()
    private var fullscreenState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

        player = PlayerService.getInstancePlayer()
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
        altImgView = view.findViewById(R.id.player_alt_img)

        seekBar = view.findViewById(R.id.player_controls_seek)
        seekBar.max = SEEK_STEP.toInt()

        // setup player-view
        playerView = VLCVideoLayout(this.requireContext())
        view.findViewById<FrameLayout>(R.id.player_playerholder).addView(playerView)

        wireObservers()
        wireActionHandlers(view)
    }

    override fun onResume() {
        super.onResume()

        attachPlayer()
        player.registerPlayerCallback(ContextCompat.getMainExecutor(this.requireContext()), playerCallback)
        refreshPlayerControls()

        setupMainMenu()
    }

    override fun onPause() {
        super.onPause()

        player.clearVideoOutput()
        player.unregisterPlayerCallback(playerCallback)

        mainMenu = null
    }

    private fun refreshPlayerControls() {
        if (player.castTo<VlcPlayer>().isPlaying()) {
            playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
        }else {
            playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
        }
    }

    private fun attachPlayer() {
        player.setVideoOutput(playerView)
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

        // seek-bar
        seekBar.seekListener = seekHandler

        // toggle viewModel.timeTextAsRemaining on click at timeTextView
        timeTextView.setOnClickListener {
            viewModel.setTimeTextAsRemaining(!viewModel.timeTextAsRemaining.value!!)
        }

        // back-pressed
        this.requireActivity().onBackPressedDispatcher.addCallback(SmartBackPressedCallback(this.lifecycle, this::onBackPressed))
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
        if(player.isPlaying())
            player.pause()
        else if(player.getCurrentMedia() !== null)
            player.play()
    }

    private fun onFullscreenClick() {
        viewModel.setFullscreenMode(!(viewModel.isFullscreen.value ?: false))
    }

    private fun onBackPressed() {
        // check fullscreen is active
        if(mainVM.fullScreenContent.value !== null){
            viewModel.setFullscreenMode(false)
        }
    }
    //endregion

    //region livedata-handlers
    private fun wireObservers(){
        viewModel.isFullscreen.observe(this.viewLifecycleOwner){
            onIsFullscreenChanged(it)
        }

        viewModel.timeTextAsRemaining.observe(this.viewLifecycleOwner){
            timeTextView.text = formatPlayTime(player.currentPosition, player.duration, it!!)
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
            chaptersItem.isEnabled = player.getChapters().isNotEmpty()
        }
    }

    private fun showChapterMenu(){
        val dlgBuilder = AlertDialog.Builder(this.requireContext())

        // generate chapter items
        val chapters = player.getChapters()
        val itemTexts = chapters.map {
            val start = Utils.formatTime(it.start)
            val end = Utils.formatTime(it.end)
            return@map "${it.name} ($start - $end)"
        }.toTypedArray()

        dlgBuilder.setItems(itemTexts){ _, itemIdx ->
            // jump to chapter
            val chapter = chapters[itemIdx]
            player.seekTo(chapter.start)
        }

        dlgBuilder.create().show()
    }

    private fun updatePlaylistMenuItem() {
        mainMenu?.let {
            it.findItem(R.id.player_menu_playlist).isEnabled = player.getCurrentPlaylist() != null
        }
    }

    private fun showPlaylistOverview() {
        PlaylistOverviewPopup().createAndShow()
    }
    //endregion

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
                if (player.isPlaying()) {// only re-hide if playing
                    runControlsTransition(false)
                }

                controlsHideCoroutine = null
            }
        }
    }

    private fun showFullscreen(fullscreen: Boolean){
        if(fullscreen == fullscreenState) return
        fullscreenState = fullscreen

        if(fullscreen){
            val parent = playerContainer.parent as ViewGroup
            parent.removeView(playerContainer)
            mainVM.fullScreenContent.postValue(playerContainer)

            // wait until view is attached to re-attach player
            CoroutineScope(Dispatchers.Main).launch {
                var success = false
                for(i in 0..100){
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
                for(i in 0..100){
                    delay(10)
                    if(playerContainer.parent === null){
                        success = true
                        break
                    }
                }
                if(success){
                    this@Player.requireView().findViewById<FrameLayout>(R.id.player_player_container_container)
                        .addView(playerContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    delay(10)
                    attachPlayer()
                }else{
                    Log.e("Player", "could not re-attach playerView: not released by parent")
                }
            }
        }
    }

    private fun updateAltImg(mediaFile: MediaFile) {
        if(mediaFile.type == MediaFile.Type.VIDEO) {
            altImgView.visibility = View.GONE
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                VlcManagers.getMediaDB().getSubject().getThumbnail(mediaFile, -1, -1).let {
                    withContext(Dispatchers.Main) {
                        altImgView.visibility = View.VISIBLE
                        if(it !== null) {
                            altImgView.setImageBitmap(it)
                        } else {
                            altImgView.setImageResource(R.drawable.ic_baseline_audiotrack_24)
                        }
                    }
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

    //region inner classes
    private inner class ControlsGestureHandler(private val view: View) : GestureDetector.SimpleOnGestureListener(){

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            view.performClick()

            runControlsTransition(!controlsVisible)

            return true
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if(e.actionMasked != MotionEvent.ACTION_UP) return false
            if(player.getCurrentMedia() === null) return false

            val width = view.width
            val x = e.x

            if(x <= width * SEEK_AREA_WIDTH){
                // seek back
                player.seekTo(-SEEK_AMOUNT)

                return true
            }else if(x >= width * (1.0f - SEEK_AREA_WIDTH)){
                // seek forward
                player.seekTo(SEEK_AMOUNT)

                return true
            }

            return false
        }
    }

    private inner class SeekHandler : SeekBar.OnSeekBarChangeListener{

        var isSeeking = false

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if(isSeeking)
                player.seekTo((player.duration * (this@Player.seekBar.value / SEEK_STEP)).toLong())
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
            if(player.isPlaying()) {// only re-hide if playing
                hideControlsWithDelay()
            }
        }
    }

    private inner class PlaylistOverviewPopup {

        private val tintSelected = ColorStateList.valueOf(this@Player.requireContext().getColor(R.color.button_selected))
        private val tintNotSelected = ColorStateList.valueOf(this@Player.requireContext().getColor(R.color.button_not_selected))

        private val listAdapter = setupListAdapter()
        private var popup: PopupWindow by OnceSettable()
        private var btnRepeat: ImageButton by OnceSettable()
        private var btnShuffle: ImageButton by OnceSettable()

        fun createAndShow() {
            loadItems()

            popup = Utils.showPopup(this@Player.requireContext(), this@Player.requireView(),
                R.layout.player_playlist_fragment,
                true,
                this@PlaylistOverviewPopup::initContent)

            setupCurrentItemObserver()
        }

        private fun initContent(view: View) {
            // prevent close when main-content is clicked
            catchAllTouches(view.findViewById(R.id.player_plfrgm_content))

            setupRecyclerView(view.findViewById(R.id.player_plfrgm_list))
            setupButtons(
                view.findViewById(R.id.player_plfrgm_rep),
                view.findViewById(R.id.player_plfrgm_shuf)
            )
        }

        private fun catchAllTouches(view: View) {
            view.setOnTouchListener { v, event ->
                if(event.actionMasked == MotionEvent.ACTION_UP)
                    v.performClick()

                true
            }
        }

        private fun setupRecyclerView(view: RecyclerView) {
            view.layoutManager = LinearLayoutManager(this@Player.requireContext(),
                LinearLayoutManager.VERTICAL, false)
            view.adapter = listAdapter
        }

        private fun setupButtons(repeat: ImageButton, shuffle: ImageButton) {
            btnRepeat = repeat
            btnShuffle = shuffle

            repeat.backgroundTintList = if(player.repeatMode == SessionPlayer.REPEAT_MODE_ALL) tintSelected else tintNotSelected
            shuffle.backgroundTintList = if(player.shuffleMode == SessionPlayer.SHUFFLE_MODE_ALL) tintSelected else tintNotSelected

            repeat.setOnClickListener {
                onRepeatClick()
            }
            shuffle.setOnClickListener {
                onShuffleClick()
            }
        }

        private fun setupCurrentItemObserver() {
            val mediaChangedListener = object : VlcPlayer.PlayerCallback() {
                override fun onCurrentMediaItemChanged(player: SessionPlayer, item: MediaItem?) {
                    val select = listAdapter.getSelectExtension()
                    select.deselect()

                    if(player.currentMediaItemIndex != SessionPlayer.INVALID_ITEM_INDEX){
                        select.select(
                            player.currentMediaItemIndex,
                            false,
                            false
                        )
                    }
                }
            }

            player.registerPlayerCallback(ContextCompat.getMainExecutor(this@Player.requireContext()), mediaChangedListener)
            popup.setOnDismissListener {
                player.unregisterPlayerCallback(mediaChangedListener)
            }
        }

        private fun setupListAdapter(): FastItemAdapter<MediaFileItem> {
            val adapter = FastItemAdapter<MediaFileItem>()

            adapter.getSelectExtension().apply {
                isSelectable = false
                selectOnLongClick = false
                allowDeselection = false
                multiSelect = false
            }

            adapter.onClickListener = { _, _, _, position ->
                onItemClick(position)
                true
            }

            return adapter
        }

        private fun onItemClick(position: Int) {
            player.skipToPlaylistItem(position)
            popup.dismiss()
        }

        private fun onRepeatClick() {
            player.repeatMode = if(player.repeatMode == SessionPlayer.REPEAT_MODE_ALL)
                    SessionPlayer.REPEAT_MODE_NONE
                else
                    SessionPlayer.REPEAT_MODE_ALL

            btnRepeat.backgroundTintList = if(player.repeatMode == SessionPlayer.REPEAT_MODE_ALL) tintSelected else tintNotSelected
        }

        private fun onShuffleClick() {
            // DynamicPlaylist will run DB-Queries when re-shuffling
            CoroutineScope(Dispatchers.IO).launch {
                player.shuffleMode = if(player.shuffleMode == SessionPlayer.SHUFFLE_MODE_ALL)
                        SessionPlayer.SHUFFLE_MODE_NONE
                    else
                        SessionPlayer.SHUFFLE_MODE_ALL

                withContext(Dispatchers.Main) {
                    btnShuffle.backgroundTintList = if(player.shuffleMode == SessionPlayer.SHUFFLE_MODE_ALL) tintSelected else tintNotSelected
                    // shuffle can change the items
                    loadItems()
                }
            }
        }

        private fun loadItems() {
            val pl = player.getCurrentPlaylist()!!
            CoroutineScope(Dispatchers.IO).launch {
                pl.getItems().map {
                    MediaFileItem(it, isDraggable = false, useTitle = true, showThumb = true)
                }.let {
                    withContext(Dispatchers.Main) {
                        listAdapter.setNewList(it)

                        listAdapter.getSelectExtension().select(
                            pl.currentPosition.coerceAtLeast(0),
                            false,
                            false
                        )
                    }
                }
            }
        }
    }

    private inner class PlayerListener : VlcPlayer.PlayerCallback() {

        override fun onPlayerStateChanged(player: SessionPlayer, playerState: Int) {
            if (player.castTo<VlcPlayer>().isPlaying()) {
                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
                hideControlsWithDelay()
            }else {
                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
            }
        }

        override fun onTimeChanged(player: VlcPlayer, time: Long) {
            if(!seekHandler.isSeeking) {
                seekBar.value = ((time / player.duration.toDouble()) * SEEK_STEP).toInt().coerceAtLeast(1)
            }

            timeTextView.text = formatPlayTime(time, player.duration, viewModel.timeTextAsRemaining.value!!)
        }

        override fun onChaptersChanged(player: VlcPlayer, chapters: List<Chapter>) {
            if(chapters.isEmpty()){
                seekBar.segments = emptyArray()
                seekBar.showTitle = false
            }else{
                val totalTime = player.duration.toDouble()
                assert(totalTime > 0)

                seekBar.segments = chapters.map {
                    val start = ((it.start.toDouble() / totalTime) * SEEK_STEP).toInt()
                    val end = ((it.end.toDouble() / totalTime) * SEEK_STEP).toInt()
                    SegmentedSeekBar.Segment(start, end, it.name)
                }.toTypedArray()

                seekBar.showTitle = true
            }

            updateChaptersMenuItem()
        }

        override fun onCurrentMediaItemChanged(player: SessionPlayer, item: MediaItem?) {
            if(item !== null)
                updateAltImg(player.castTo<VlcPlayer>().getCurrentMedia()!!)
        }

        override fun onPlaylistChanged(player: VlcPlayer) {
            updatePlaylistMenuItem()
        }
    }
    //endregion
}