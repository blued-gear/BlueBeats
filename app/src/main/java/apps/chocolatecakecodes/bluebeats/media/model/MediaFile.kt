package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.taglib.Chapter
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import apps.chocolatecakecodes.bluebeats.util.LazyVar

internal class MediaFile private constructor(
    internal val entityId: Long,
    override val name: String,
    var type: Type,
    parentSupplier: () -> MediaDir,
    mediaTagsSupplier: () -> TagFields = { TagFields() },
    chaptersSupplier: () -> List<Chapter>? = { null },
    usertagsSupplier: () -> List<String> = { emptyList() }
): MediaNode() {

    companion object {
        fun new(
            entityId: Long,
            name: String,
            type: Type,
            parentSupplier: () -> MediaDir,
            mediaTagsSupplier: () -> TagFields = { TagFields() },
            chaptersSupplier: () -> List<Chapter>? = { null },
            usertagsSupplier: () -> List<String> = { emptyList() }
        ) = MediaFile(entityId, name, type, parentSupplier, mediaTagsSupplier, chaptersSupplier, usertagsSupplier).also {
            // trigger caching of hashCode (else it might happen that a DB-query in the UI-Thread gets executed)
            it.hashCode()
        }
    }

    override val parent: MediaDir by CachedReference(this, NODE_CACHE_TIME) {
        parentSupplier()
    }

    override val path: String by lazy {
        parent.path + name
    }

    var mediaTags: TagFields by LazyVar<MediaFile, TagFields> {
        mediaTagsSupplier()
    }

    var chapters: List<Chapter>? by LazyVar<MediaFile, List<Chapter>?> {
        chaptersSupplier()
    }

    var userTags: List<String> by LazyVar<MediaFile, List<String>> {
        usertagsSupplier()
    }

    val title: String
        get() {
            return if(!mediaTags.title.isNullOrEmpty())
                mediaTags.title
            else
                name
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
    fun shallowEquals(other: MediaFile?): Boolean {
        if(other === null)
            return false
        return  this.type == other.type && this.path == other.path
    }

    enum class Type {
        AUDIO, VIDEO, OTHER
    }
}
