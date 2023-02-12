package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.database.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
import apps.chocolatecakecodes.bluebeats.taglib.TagParser
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import apps.chocolatecakecodes.bluebeats.util.LazyVar
import apps.chocolatecakecodes.bluebeats.util.Utils

class MediaFile internal constructor(internal val entity: MediaFileEntity): MediaNode(){

    override val parent: MediaDir by CachedReference(this, NODE_CACHE_TIME){
        val dao = RoomDB.DB_INSTANCE.mediaDirDao()
        return@CachedReference dao.getForId(entity.parent)
    }
    override val path: String by lazy{
        parent.path + name
    }
    override val name: String by entity::name

    var type: Type
        get() = entity.type
        internal set(value){
            entity.type = value
        }

    var mediaTags: TagFields by LazyVar<MediaFile, TagFields> {
        RoomDB.DB_INSTANCE.id3TagDao().getTagsOfFile(entity.id)
    }

    var chapters: List<Chapter>? by LazyVar<MediaFile, List<Chapter>?> {
        if(entity.chaptersJson.isNullOrEmpty())
            null
        else
            TagParser.Serializer.GSON.fromJson(entity.chaptersJson,
                Utils.captureType<List<Chapter>>())
    }

    var userTags: List<String> by LazyVar<MediaFile, List<String>> {
        if(entity.id == MediaNode.UNALLOCATED_NODE_ID)// can not load tags from db if this file is not saved
            emptyList()
        else
            RoomDB.DB_INSTANCE.userTagDao().getUserTagsForFile(this)
    }

    internal fun createCopy(): MediaFile{
        val copy =  MediaFile(entity.copy(id = MediaNode.UNALLOCATED_NODE_ID))
        copy.chapters = this.chapters
        copy.userTags = this.userTags
        copy.mediaTags = this.mediaTags.clone()
        return copy
    }

    override fun equals(other: Any?): Boolean {
        if(other !is MediaFile)
            return false
        if(!shallowEquals(other))
            return false

        if(other.chapters != this.chapters
                || other.mediaTags != this.mediaTags
                || other.userTags != this.userTags)
            return false
        //TODO compare all attributes

        return true
    }

    /**
     * like <code>equals(other)</code>, with the difference that just the type and path are compared
     */
    fun shallowEquals(other: MediaFile?): Boolean{
        if(other === null)
            return false
        return  this.type == other.type && this.path == other.path
    }

    enum class Type{
        AUDIO, VIDEO, OTHER
    }
}
