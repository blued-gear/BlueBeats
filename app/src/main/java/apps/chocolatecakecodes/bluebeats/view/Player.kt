package apps.chocolatecakecodes.bluebeats.view

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.Chapter
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.PlaylistItem
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.service.PlayerService
import apps.chocolatecakecodes.bluebeats.service.PlayerServiceConnection
import apps.chocolatecakecodes.bluebeats.util.Debouncer
import apps.chocolatecakecodes.bluebeats.util.EventualValue
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.SmartBackPressedCallback
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.view.specialitems.SelectableItem
import apps.chocolatecakecodes.bluebeats.view.specialitems.playlistitems.itemForPlaylistItem
import apps.chocolatecakecodes.bluebeats.view.specialviews.SegmentedSeekBar
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.select.getSelectExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.media3.common.Player as Media3Player

private const val CONTROLS_FADE_IN_TIME = 200L
private const val CONTROLS_FADE_OUT_TIME = 100L
private const val CONTROLS_FADE_OUT_DELAY = 2000L
private const val SEEK_STEP = 1000.0f
private const val SEEK_DEBOUNCE_TIMEOUT = 200L
private const val TIME_UPDATER_INTERVAL_RUNNING = 500L
private const val TIME_UPDATER_INTERVAL_IDLING = 1000L

@androidx.annotation.OptIn(UnstableApi::class)
class Player : Fragment() {

    companion object {
        fun newInstance() = Player()

        private const val SEEK_AREA_WIDTH = 0.4f// in %/100 (per side)
    }

    private val playerCallback = PlayerListener()
    private var viewModel: PlayerViewModel by OnceSettable()
    private var mainVM: MainActivityViewModel by OnceSettable()
    private var playerView: VLCVideoLayout by OnceSettable()
    private var playerContainer: ViewGroup by OnceSettable()
    private var seekBar: SegmentedSeekBar by OnceSettable()
    private var timeTextView: TextView by OnceSettable()
    private var altImgView: ImageView by OnceSettable()
    private var seekDebouncer: Debouncer<Long> by OnceSettable()
    private var mainMenu: Menu? = null
    private var controlsVisible: Boolean = true
    private var controlsHideCoroutine: Job? = null
    private val seekHandler = SeekHandler()
    private var fullscreenState: Boolean = false
    private var timeUpdaterTimerId: Int = -1

    private val player: EventualValue<PlayerServiceConnection, VlcPlayer>

    init {
        player = EventualValue(Dispatchers.Main) { it.player }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player.holder = PlayerService.connect(this.requireContext())

        val vmProvider = ViewModelProvider(this.requireActivity())
        viewModel = vmProvider.get(PlayerViewModel::class.java)
        mainVM = vmProvider.get(MainActivityViewModel::class.java)

        seekDebouncer = Debouncer.create(SEEK_DEBOUNCE_TIMEOUT) {
            withContext(Dispatchers.Main) {
                player.await().seekTo(it)
            }
        }
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

        player.await {
            attachPlayer()
            it.addListener(playerCallback)

            refreshPlayerControls()
        }

        timeUpdaterTimerId = TimerThread.INSTANCE.addInterval(TIME_UPDATER_INTERVAL_IDLING, TimeUpdater())

        setupMainMenu()
    }

    override fun onPause() {
        super.onPause()

        TimerThread.INSTANCE.removeTask(timeUpdaterTimerId)

        player.await {
            withContext(Dispatchers.Main) {
                it.clearVideoOutput()
                it.removeListener(playerCallback)
            }
        }

        mainMenu = null
    }

    override fun onDestroy() {
        seekDebouncer.stop()
        player.holder?.let { this.context?.unbindService(it) }
        player.destroy()

        super.onDestroy()
    }

    private suspend fun attachPlayer() {
        player.await().setVideoOutput(playerView)
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

        // prev and next buttons
        view.findViewById<View>(R.id.player_controls_prev).setOnClickListener {
            onPrevClick()
        }
        view.findViewById<View>(R.id.player_controls_next).setOnClickListener {
            onNextClick()
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

        val gestureDetector = GestureDetector(this.requireContext(), gestureHandler)
        gestureDetector.setOnDoubleTapListener(gestureHandler)
        gestureDetector.setIsLongpressEnabled(false)

        controlsPane.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    private fun onPlayPauseClick() {
        player.await {
            if(it.isPlaying)
                it.pause()
            else if(it.getCurrentMedia() !== null)
                it.play()
        }
    }

    private fun onPrevClick() {
        player.await { it.seekToPrevious() }
    }

    private fun onNextClick() {
        player.await { it.seekToNext() }
    }

    private fun onFullscreenClick() {
        viewModel.setFullscreenMode(!(viewModel.isFullscreen.value ?: false))
    }

    private fun onBackPressed() {
        // check fullscreen is active
        if(mainVM.fullScreenContent.value !== null) {
            viewModel.setFullscreenMode(false)
        }
    }
    //endregion

    //region livedata-handlers
    private fun wireObservers(){
        viewModel.isFullscreen.observe(this.viewLifecycleOwner) {
            onIsFullscreenChanged(it)
        }

        viewModel.timeTextAsRemaining.observe(this.viewLifecycleOwner) { timeTextAsRemaining ->
            player.await { player ->
                timeTextView.text = formatPlayTime(player.currentPosition, player.duration, timeTextAsRemaining)
            }
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
                CoroutineScope(Dispatchers.Main).launch { showChapterMenu() }
                return@setOnMenuItemClickListener true
            }
            menu.findItem(R.id.player_menu_playlist).setOnMenuItemClickListener {
                CoroutineScope(Dispatchers.Main).launch { showPlaylistOverview() }
                return@setOnMenuItemClickListener true
            }

            CoroutineScope(Dispatchers.Main).launch {
                updateChaptersMenuItem()
                updatePlaylistMenuItem()
            }
        }
    }

    private suspend fun updateChaptersMenuItem(){
        mainMenu?.let {
            val chaptersItem = it.findItem(R.id.player_menu_chapters)
            chaptersItem.isEnabled = player.await().getChapters().isNotEmpty()
        }
    }

    private suspend fun showChapterMenu(){
        val dlgBuilder = AlertDialog.Builder(this.requireContext())
        val player = player.await()

        // generate chapter items
        val chapters = player.getChapters()
        val itemTexts = chapters.map {
            val start = Utils.formatTime(it.start)
            val end = Utils.formatTime(it.end)
            return@map "${it.name} ($start - $end)"
        }.toTypedArray()

        dlgBuilder.setItems(itemTexts) { _, itemIdx ->
            // jump to chapter
            val chapter = chapters[itemIdx]
            player.seekTo(chapter.start)
        }

        dlgBuilder.create().show()
    }

    private suspend fun updatePlaylistMenuItem() {
        mainMenu?.let {
            it.findItem(R.id.player_menu_playlist).isEnabled = player.await().getCurrentPlaylist() != null
        }
    }

    private suspend fun showPlaylistOverview() {
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
                if (player.await().isPlaying()) {// only re-hide if playing
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

    private suspend fun refreshPlayerControls() {
        val player = player.await()

        if (player.isPlaying()) {
            playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
        }else {
            playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
        }

        player.getCurrentMedia()?.let {
            updateAltImg(it)
            updateChapters(player.getChapters())

            if(!seekHandler.isSeeking) {
                seekBar.value = ((player.currentPosition / player.duration.toDouble()) * SEEK_STEP).toInt().coerceAtLeast(1)
            }
            timeTextView.text = formatPlayTime(player.currentPosition, player.duration, viewModel.timeTextAsRemaining.value!!)
        } ?: run {
            updateChapters(emptyList())

            seekBar.value = 1
            timeTextView.text = formatPlayTime(0, 0, viewModel.timeTextAsRemaining.value!!)
        }

        updatePlaylistMenuItem()
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

    private suspend fun updateChapters(chapters: List<Chapter>) {
        if(chapters.isEmpty()){
            seekBar.segments = emptyArray()
            seekBar.showTitle = false
        }else{
            val totalTime = player.await().duration.toDouble()
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

            return runBlocking {
                val player = player.await()
                if(player.getCurrentMedia() === null) return@runBlocking false

                val width = view.width
                val x = e.x

                if(x <= width * SEEK_AREA_WIDTH){
                    // seek back
                    player.seekBack()

                    return@runBlocking true
                }else if(x >= width * (1.0f - SEEK_AREA_WIDTH)){
                    // seek forward
                    player.seekForward()

                    return@runBlocking true
                }

                return@runBlocking false
            }
        }
    }

    private inner class SeekHandler : SeekBar.OnSeekBarChangeListener {

        var isSeeking = false

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if(isSeeking) {
                player.await { player ->
                    val newTime = (player.duration * (this@Player.seekBar.value / SEEK_STEP)).toLong()
                    timeTextView.text = formatPlayTime(newTime, player.duration, viewModel.timeTextAsRemaining.value!!)
                    seekDebouncer.debounce(newTime)
                }
            }
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
            player.await { player ->
                if(player.isPlaying()) {// only re-hide if playing
                    hideControlsWithDelay()
                }
            }
        }
    }

    private inner class PlaylistOverviewPopup : Media3Player.Listener {

        private val tintSelected = ColorStateList.valueOf(this@Player.requireContext().getColor(R.color.button_selected))
        private val tintNotSelected = ColorStateList.valueOf(this@Player.requireContext().getColor(R.color.button_not_selected))

        private val listAdapter = setupListAdapter()
        private var listView: RecyclerView by OnceSettable()
        private var popup: PopupWindow by OnceSettable()
        private var btnRepeat: ImageButton by OnceSettable()
        private var btnShuffle: ImageButton by OnceSettable()
        private var player: VlcPlayer by OnceSettable()
        private var expectedPl: List<PlaylistItem> = emptyList()

        suspend fun createAndShow() {
            player = this@Player.player.await()

            withContext(Dispatchers.IO) {
                loadItems()
            }

            popup = Utils.showPopup(this@Player.requireContext(), this@Player.requireView(),
                R.layout.player_playlist_fragment,
                true,
                this@PlaylistOverviewPopup::initContent)

            player.addListener(this)
            popup.setOnDismissListener {
                player.removeListener(this)
            }
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

            listView = view

            scrollToCurrentItem()
        }

        private fun setupButtons(repeat: ImageButton, shuffle: ImageButton) {
            btnRepeat = repeat
            btnShuffle = shuffle

            setRepeatButtonContent(repeat, player.repeatMode)
            shuffle.backgroundTintList = if(player.shuffleModeEnabled) tintSelected else tintNotSelected

            repeat.setOnClickListener {
                onRepeatClick()
            }
            shuffle.setOnClickListener {
                onShuffleClick()
            }
        }

        private fun setRepeatButtonContent(btn: ImageButton, repeatMode: Int) {
            when(repeatMode) {
                Media3Player.REPEAT_MODE_ALL -> {
                    btn.setImageResource(R.drawable.ic_baseline_repeat_24)
                    btn.backgroundTintList = tintSelected
                }
                Media3Player.REPEAT_MODE_ONE -> {
                    btn.setImageResource(R.drawable.baseline_repeat_one_24)
                    btn.backgroundTintList = tintSelected
                }
                Media3Player.REPEAT_MODE_OFF -> {
                    btn.setImageResource(R.drawable.ic_baseline_repeat_24)
                    btn.backgroundTintList = tintNotSelected
                }
            }
        }

        private fun setupListAdapter(): FastItemAdapter<SelectableItem<out SelectableItem.ViewHolder<*>>> {
            val adapter = FastItemAdapter<SelectableItem<out SelectableItem.ViewHolder<*>>>()

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
            player.seekToDefaultPosition(position)
            popup.dismiss()
        }

        private fun onRepeatClick() {
            player.repeatMode = when(player.repeatMode) {
                Media3Player.REPEAT_MODE_OFF -> Media3Player.REPEAT_MODE_ALL
                Media3Player.REPEAT_MODE_ALL -> Media3Player.REPEAT_MODE_ONE
                Media3Player.REPEAT_MODE_ONE -> Media3Player.REPEAT_MODE_OFF
                else -> throw AssertionError()
            }
        }

        private fun onShuffleClick() {
            player.shuffleModeEnabled = !player.shuffleModeEnabled
        }

        /** must be called in IO Dispatcher */
        private suspend fun loadItems() {
            val pl = player.getCurrentPlaylist()!!
            val items = pl.getItems()

            expectedPl = ArrayList(pl.getItems())

            items.map {
                itemForPlaylistItem(it, false)
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

        private fun scrollToCurrentItem() {
            val pl = player.getCurrentPlaylist()!!
            val scroller = LinearSmoothScroller(listView.context)
            val itemPos = pl.currentPosition.coerceAtLeast(0)
            scroller.targetPosition = itemPos
            listView.layoutManager!!.startSmoothScroll(scroller)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val select = listAdapter.getSelectExtension()
            select.deselect()

            val pl = player.getCurrentPlaylist()
            val plPos = pl?.currentPosition ?: -1
            if(plPos != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (expectedPl != pl?.getItems()) {
                        loadItems()
                        scrollToCurrentItem()
                    }

                    withContext(Dispatchers.Main) {
                        select.select(
                            player.currentMediaItemIndex,
                            false,
                            false
                        )
                    }
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            setRepeatButtonContent(btnRepeat, player.repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            btnShuffle.backgroundTintList = if(player.shuffleModeEnabled) tintSelected else tintNotSelected

            // shuffle can change the items
            CoroutineScope(Dispatchers.IO).launch {
                loadItems()
            }
        }
    }

    private inner class PlayerListener : Media3Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if(isPlaying) {
                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_pause)
                hideControlsWithDelay()
            } else {
                playerContainer.findViewById<ImageButton>(R.id.player_controls_play).setImageResource(R.drawable.ic_baseline_play)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            player.await { player ->
                player.getCurrentMedia()?.let { media ->
                    updateAltImg(media)
                }
                updateChapters(player.getChapters())
            }
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            CoroutineScope(Dispatchers.Main).launch {
                updatePlaylistMenuItem()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            this@Player.context?.let { ctx ->
                Toast.makeText(ctx, R.string.player_vlc_err, Toast.LENGTH_LONG)
            }
        }
    }

    private inner class TimeUpdater : TimerThread.TaskRunnable {
        override operator fun invoke(): Long {
            // I don't know why this can be called after onPause() removed the timer, so add this safeguard
            if(this@Player.activity == null)
                return 0L

            return runBlocking {
                val player = player.await()

                withContext(Dispatchers.Main) {
                    val time = player.currentPosition
                    if(time >= 0) {
                        if(!seekHandler.isSeeking) {
                            seekBar.value = ((time / player.duration.toDouble()) * SEEK_STEP).toInt().coerceAtLeast(1)
                        }

                        timeTextView.text = formatPlayTime(time, player.duration, viewModel.timeTextAsRemaining.value!!)
                    }

                    return@withContext if(player.isPlaying)
                        TIME_UPDATER_INTERVAL_RUNNING
                    else
                        TIME_UPDATER_INTERVAL_IDLING
                }
            }
        }
    }
    //endregion
}