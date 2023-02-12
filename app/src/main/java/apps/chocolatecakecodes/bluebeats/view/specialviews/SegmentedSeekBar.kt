package apps.chocolatecakecodes.bluebeats.view.specialviews

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsoluteLayout
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSeekBar
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.util.OnceSettable

/**
 * This view shows a seek-bar with segment-markers
 * (segments have a start, an end (ranging from 0 and this.max) and a title).
 * Optionally a TextView with the title of the current segment is shown above the seeker.
 */
class SegmentedSeekBar : FrameLayout {

    private var seekBar: AppCompatSeekBar by OnceSettable()
    private var sectionTitle: TextView by OnceSettable()
    private var markerContainer: AbsoluteLayout by OnceSettable()
    private var markers: Array<View> = emptyArray()

    var max: Int
        get(){
            return seekBar.max
        }
        set(value){
            seekBar.max = value
        }
    var value: Int
        get(){
            return seekBar.progress
        }
        set(value){
            seekBar.progress = value
        }
    var seekListener: SeekBar.OnSeekBarChangeListener? = null
    var markerColor: Int = Color.BLUE
        set(value){
            field = value

            // update views
            for(m in markers)
                m.setBackgroundColor(value)
            sectionTitle.setTextColor(value)
        }
    var titleColor: Int
        get(){
            return sectionTitle.textColors.defaultColor
        }
        set(value){
            sectionTitle.setTextColor(value)
        }
    var showTitle: Boolean = true
        set(value){
            field = value
            sectionTitle.visibility = if(value) View.VISIBLE else View.GONE
        }

    var currentSegment: Segment? = null
        private set
    var segments: Array<Segment> = emptyArray()
        set(value) {
            field = value
            currentSegment = null
            createMarkers()
        }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        setup(context)
        loadAttributes(context, attrs, defStyleAttr)
    }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes){
        setup(context)
        loadAttributes(context, attrs, defStyleAttr)
    }

    private fun setup(context: Context){
        val view = LayoutInflater.from(context).inflate(R.layout.segmented_seek_bar, this)

        seekBar = view.findViewById(R.id.ssb_seek)
        sectionTitle = view.findViewById(R.id.ssb_title)
        markerContainer = view.findViewById(R.id.ssb_markers)

        seekBar.setOnSeekBarChangeListener(SeekHandler())

        this.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun loadAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int){
        val style: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SegmentedSeekBar,
            R.attr.default_SegmentedSeekBarStyle, R.style.default_SegmentedSeekBarStyle)

        max = style.getInt(R.styleable.SegmentedSeekBar_max, 100)
        value = style.getInt(R.styleable.SegmentedSeekBar_value, 0)
        markerColor = style.getColor(R.styleable.SegmentedSeekBar_markerColor, Color.BLUE)
        titleColor = style.getColor(R.styleable.SegmentedSeekBar_titleColor, Color.WHITE)
        showTitle = style.getBoolean(R.styleable.SegmentedSeekBar_showTitle, true)

        style.recycle()
    }

    private fun createMarkers(){
        // clear old makers
        for(m in markers)
            markerContainer.removeView(m)

        markers = Array(segments.size){
            val segment = segments[it]

            // add at right position
            val pos = (segment.start.toFloat() / max) * (seekBar.width - 4 * seekBar.thumbOffset.toFloat())
            val markerX = (seekBar.x + pos).toInt()
            val marker = createMarker(markerX)

            markerContainer.addView(marker)

            return@Array marker
        }
    }

    private fun createMarker(x: Int): View{
        val marker = View(this.context)
        val lp = AbsoluteLayout.LayoutParams(4, AbsoluteLayout.LayoutParams.MATCH_PARENT, x, 10)
        marker.layoutParams = lp
        marker.setBackgroundColor(markerColor)
        return marker
    }

    public class Segment(val start: Int, val end: Int, val title: String){
        constructor(start: Int, end: Int): this(start, end, "")
    }

    private inner class SeekHandler : SeekBar.OnSeekBarChangeListener{

        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            currentSegment = progressToSegment(progress)

            if(showTitle){
                val segment = currentSegment
                if(segment !== null){
                    sectionTitle.text = segment.title

                    // position the sectionTitle centered over the thumb
                    val pos = (progress.toFloat() / max) * (seekBar.width - 4 * seekBar.thumbOffset.toFloat())
                    sectionTitle.x = (seekBar.x + pos + seekBar.thumbOffset) - (sectionTitle.width / 4) + 10
                }else{
                    sectionTitle.text = ""
                }
            }

            seekListener?.onProgressChanged(null, progress, fromUser)
        }

        override fun onStartTrackingTouch(sb: SeekBar?) {
            seekListener?.onStartTrackingTouch(null)
        }

        override fun onStopTrackingTouch(sb: SeekBar?) {
            seekListener?.onStopTrackingTouch(null)
        }

        private fun progressToSegment(progress: Int): Segment?{
            val segment = currentSegment
            if(segment !== null)
                if(progress >= segment.start && progress < segment.end)
                    return segment

            for(s in segments)
                if(progress >= s.start && progress < s.end)
                    return s

            return null
        }
    }
}
