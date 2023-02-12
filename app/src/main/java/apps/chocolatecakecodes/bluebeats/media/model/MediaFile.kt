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

    private var loadedChapters: List<Chapter>? = null
    private var chaptersLoaded = false
    var chapters: List<Chapter>?
        get(){
            synchronized(this){
                if(!chaptersLoaded){
                    if(entity.chaptersJson.isNullOrEmpty())
                        loadedChapters = null
                    else
                        loadedChapters = TagParser.Serializer.GSON.fromJson(entity.chaptersJson,
                            Utils.captureType<List<Chapter>>())

                    chaptersLoaded = true
                }

                return loadedChapters
            }
        }
        internal set(value) {
            synchronized(this){
                loadedChapters = value
                chaptersLoaded = true

                entity.chaptersJson = if(value === null) null else TagParser.Serializer.GSON.toJson(value)
            }
        }

    private var loadedUserTags: List<String>? = null
    var userTags: List<String>
        get(){
            synchronized(this){
                if(loadedUserTags === null){
                    if(entity.id == MediaNode.UNALLOCATED_NODE_ID){// can not load tags from db if this file is not saved
                        return emptyList()
                    }

                    loadedUserTags = RoomDB.DB_INSTANCE.userTagDao().getUserTagsForFile(this)
                }

                return loadedUserTags!!
            }
        }
        internal set(value){
            synchronized(this) {
                loadedUserTags = value
            }
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
