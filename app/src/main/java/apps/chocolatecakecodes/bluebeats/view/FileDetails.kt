package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.VlcManagers
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
import apps.chocolatecakecodes.bluebeats.util.OnceSettable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATE_FILE = "key:filePath"

class FileDetails() : Fragment(R.layout.filedetails_fragment) {

    var file: MediaFile by OnceSettable()

    private lateinit var tagListView: LinearLayout
    private lateinit var usertagListView: LinearLayout
    private lateinit var usertagListTitle: TextView

    constructor(file: MediaFile) : this() {
        this.file = file
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState !== null){
            val filePath = savedInstanceState.getString(STATE_FILE)!!
            file = VlcManagers.getMediaDB().getSubject().pathToMedia(filePath) as MediaFile
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

        showData()
    }

    private fun showData(){
        // tags could be loaded from DB
        CoroutineScope(Dispatchers.IO).launch {
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

        tags.title?.let{
            val name = ctx.getString(R.string.tagname_title) + ":"
            tagListView.addView(TagEntry(name, it, ctx), lp)
        }

        if(tags.length > 0){
            val name = ctx.getString(R.string.tagname_length) + ":"
            val value = tags.length.toString()
            tagListView.addView(TagEntry(name, value, ctx), lp)
        }

        tags.artist?.let{
            val name = ctx.getString(R.string.tagname_artist) + ":"
            tagListView.addView(TagEntry(name, it, ctx), lp)
        }
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
