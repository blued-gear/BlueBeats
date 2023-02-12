package apps.chocolatecakecodes.bluebeats.view

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.DialogFragment
import apps.chocolatecakecodes.bluebeats.R
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
import apps.chocolatecakecodes.bluebeats.taglib.UserTags

class FileDetails(private val file: MediaFile) : Fragment(R.layout.filedetails_fragment) {

    private lateinit var tagListView: LinearLayout
    private lateinit var usertagListView: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tagListView = view.findViewById(R.id.filedetails_taglist)
        usertagListView = view.findViewById(R.id.filedetails_usertaglist)

        showData()
    }

    private fun showData(){
        showTags(file.mediaTags)
        showUsertags(file.userTags)
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
