package apps.chocolatecakecodes.bluebeats.media

import android.util.Log
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.Chapter
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.TagFields
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.UserTags
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileImpl
import com.mpatric.mp3agic.AbstractID3v2Tag
import com.mpatric.mp3agic.BufferTools
import com.mpatric.mp3agic.EncodedText
import com.mpatric.mp3agic.ID3v2Frame
import com.mpatric.mp3agic.ID3v2TextFrameData
import com.mpatric.mp3agic.InvalidDataException
import com.mpatric.mp3agic.Mp3File
import org.videolan.libvlc.interfaces.IMedia

internal class MediaParser {

    companion object {
        private const val LOG_TAG = "MediaParser"
    }

    private val mediaDirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }

    fun parseFile(file: IMedia, parent: MediaDir): MediaFileImpl {
        assert(file.type == IMedia.Type.File)

        val name = file.uri.lastPathSegment ?: throw IllegalArgumentException("media has invalid path")

        if(!file.isParsed)
            file.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

        val type = detectType(file)
        val mf = MediaFileImpl.new(
            MediaNode.UNALLOCATED_NODE_ID,
            name,
            type,
            { mediaDirDao.getForId(parent.id) }
        )

        parseAndUpdateTags(mf)

        if(mf.mediaTags.length < 1) {
            // parse may have failed; set by libvlc
            mf.mediaTags = mf.mediaTags.copy(length = file.duration)
        }

        //TODO parse more attributes

        return mf
    }

    private fun detectType(file: IMedia): MediaFile.Type {
        for(i in 0 until (file.tracks?.size ?: 0)) {
            if (file.tracks[i].type == IMedia.Track.Type.Video) {
                return MediaFile.Type.VIDEO
            }
        }

        for (i in 0 until (file.tracks?.size ?: 0)) {
            if (file.tracks[i].type == IMedia.Track.Type.Audio) {
                return MediaFile.Type.AUDIO
            }
        }

        return MediaFile.Type.OTHER
    }

    private fun parseAndUpdateTags(file: MediaFileImpl) {
        if(file.type != MediaFile.Type.AUDIO || !file.name.endsWith(".mp3")) return

        try {
            val parser = Mp3File(file.path)
            file.mediaTags = readStandardTags(parser)
            file.chapters = readChapters(parser)
            file.userTags = readUsertags(parser)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "exception while parsing MP3 tags (file: ${file.path})", e)
        }
    }

    private fun readStandardTags(parser: Mp3File): TagFields {
        var title: String? = null
        var artist: String? = null
        var genre: String? = null

        if(parser.hasId3v1Tag()) {
            val data = parser.id3v1Tag
            title = data.title
            artist = data.artist
            genre = data.genreDescription
        }

        if(parser.hasId3v2Tag()) {
            val data = parser.id3v2Tag
            title = data.title
            artist = data.artist
            genre = data.genreDescription
        }

        val length = parser.lengthInMilliseconds

        return TagFields(
            title = title,
            artist = artist,
            genre = genre,
            length = length
        )
    }

    private fun readChapters(parser: Mp3File): List<Chapter>? {
        if(parser.hasId3v2Tag()) {
            return parser.id3v2Tag.chapters?.map {
                val title = it.subframes.first { it.id == AbstractID3v2Tag.ID_TITLE }?.let {
                    ID3v2TextFrameData(it.hasUnsynchronisation(), it.data).text.toString()
                } ?: it.id

                Chapter(
                    it.startTime.toLong(),
                    it.endTime.toLong(),
                    title
                )
            }
        }
        return null
    }

    private fun readUsertags(parser: Mp3File): List<String> {
        if(parser.hasId3v2Tag()) {
            parser.id3v2Tag.frameSets["TXXX"]?.frames?.forEach { frame ->
                try {
                    val (desc, text) = decodeTXXXFrame(frame)
                    if(desc == UserTags.Parser.USERTEXT_KEY) {
                        return UserTags.Parser.parse(text).tags
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "unable to decode TXXX frame (file: ${parser.filename})", e)
                }
            }
        }
        return emptyList()
    }

    // code adapted from ID3v2UrlFrameData
    private fun decodeTXXXFrame(frame: ID3v2Frame): Pair<String, String> {
        val bytes = if(frame.hasUnsynchronisation()) BufferTools.synchroniseBuffer(frame.data) else frame.data

        val description: EncodedText
        val text: EncodedText

        var marker = BufferTools.indexOfTerminatorForEncoding(bytes, 1, bytes[0].toInt())
        if (marker >= 0) {
            description = EncodedText(bytes[0], BufferTools.copyBuffer(bytes, 1, marker - 1))
            marker += description.terminator.size
        } else {
            description = EncodedText(bytes[0], "")
            marker = 1
        }

        text = EncodedText(bytes[0], BufferTools.copyBuffer(bytes, marker, bytes.size - marker))

        @Suppress("USELESS_ELVIS")
        return Pair(
            description.toString() ?: throw InvalidDataException("TXXX description could not be decoded"),
            text.toString() ?: throw InvalidDataException("TXXX text could not be decoded")
        )
    }

}