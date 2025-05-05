package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.TagFields
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import apps.chocolatecakecodes.bluebeats.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATE_FILE = "key:filePath"

internal class FileDetails() : Fragment(R.layout.filedetails_fragment) {

    var file: MediaFile by OnceSettable()

    private lateinit var tagListView: LinearLayout
    private lateinit var usertagListView: LinearLayout
    private lateinit var usertagListTitle: TextView
    private lateinit var thumbView: ImageView

    private var stateLoaderJob: Job? = null

    constructor(file: MediaFile) : this() {
        this.file = file
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState !== null){
            val filePath = savedInstanceState.getString(STATE_FILE)!!
            stateLoaderJob = CoroutineScope(Dispatchers.IO).launch {
                file = VlcManagers.getMediaDB().getSubject().pathToMedia(filePath) as MediaFile
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(STATE_FILE, file.path)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tagListView = view.findViewById(R.id.filedetails_taglist)
        usertagListView = view.findViewById(R.id.filedetails_usertaglist)
        usertagListTitle = view.findViewById(R.id.filedetails_usertags_title)
        thumbView = view.findViewById(R.id.filedetails_thumb)

        showData()
    }

    private fun showData(){
        CoroutineScope(Dispatchers.IO).launch {
            if(stateLoaderJob !== null) {
                stateLoaderJob!!.join()
                stateLoaderJob = null
            }

            showThumb()
            showName()

            file.mediaTags.let {
                withContext(Dispatchers.Main){
                    showTags(it)
                }
            }
            file.userTags.let {
                withContext(Dispatchers.Main){
                    showUsertags(it)
                }
            }
        }
    }

    private suspend fun showName() {
        val ctx = this.requireContext()
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val title = ctx.getString(R.string.tagname_filepath) + ":"
        val name = file.path

        withContext(Dispatchers.Main) {
            tagListView.addView(TagEntry(title, name, ctx), lp)
        }
    }

    private fun showTags(tags: TagFields){
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val ctx = this.requireContext()

        addTag(R.string.tagname_title, tags.title)

        if(tags.length > 0){
            val name = ctx.getString(R.string.tagname_length) + ":"
            val value = Utils.formatTime(tags.length)
            tagListView.addView(TagEntry(name, value, ctx), lp)
        }

        addTag(R.string.tagname_artist, tags.artist)
        addTag(R.string.tagname_genre, tags.genre)
    }

    private fun showUsertags(tags: List<String>){
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        val ctx = this.requireContext()

        tags.forEach {
            val entryView = UsertagEntry(it, ctx)
            usertagListView.addView(entryView, lp)
        }

        usertagListTitle.visibility = if(tags.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun showThumb() {
        this.requireView().doOnNextLayout { view ->
            CoroutineScope(Dispatchers.IO).launch {
                VlcManagers.getMediaDB().getSubject()
                    .getThumbnail(file, view.width, -1)
                    .let {
                        withContext(Dispatchers.Main) {
                            if (it !== null) {
                                thumbView.visibility = View.VISIBLE
                                thumbView.setImageBitmap(it)
                            } else {
                                thumbView.visibility = View.GONE
                            }
                        }
                    }
            }
        }
    }

    private fun addTag(name: Int, value: String?){
        if(!value.isNullOrEmpty()){
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val ctx = this.requireContext()

            val nameStr = ctx.getString(name) + ":"
            tagListView.addView(TagEntry(nameStr, value, ctx), lp)
        }
    }
}

private class TagEntry(val name: String, val value: String, ctx: Context) : FrameLayout(ctx) {
    init{
        val content = LayoutInflater.from(ctx).inflate(R.layout.filedetails_entry_tag, this)
        content.findViewById<TextView>(R.id.filedetails_tag_entry_name).text = name
        content.findViewById<TextView>(R.id.filedetails_tag_entry_value).text = value
    }
}

private class UsertagEntry(val value: String, ctx: Context) : FrameLayout(ctx) {
    init{
        val content = LayoutInflater.from(ctx).inflate(R.layout.filedetails_entry_usertag, this)
        content.findViewById<TextView>(R.id.filedetails_usertag_entry_value).text = value
    }
}
