package apps.chocolatecakecodes.bluebeats.media.model

import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.Chapter
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.TagFields
import apps.chocolatecakecodes.bluebeats.util.CachedReference
import apps.chocolatecakecodes.bluebeats.util.LazyVar

internal class MediaFileImpl private constructor(
    override val id: Long,
    override val name: String,
    override var type: Type,
    parentSupplier: () -> MediaDir,
    mediaTagsSupplier: () -> TagFields = { TagFields() },
    chaptersSupplier: () -> List<Chapter>? = { null },
    usertagsSupplier: () -> List<String> = { emptyList() }
): MediaFile() {

    companion object {
        fun new(
            entityId: Long,
            name: String,
            type: Type,
            parentSupplier: () -> MediaDir,
            mediaTagsSupplier: () -> TagFields = { TagFields() },
            chaptersSupplier: () -> List<Chapter>? = { null },
            usertagsSupplier: () -> List<String> = { emptyList() }
        ) = MediaFileImpl(entityId, name, type, parentSupplier, mediaTagsSupplier, chaptersSupplier, usertagsSupplier).also {
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

    override var mediaTags: TagFields by LazyVar<MediaFile, TagFields> {
        mediaTagsSupplier()
    }

    override var chapters: List<Chapter>? by LazyVar<MediaFile, List<Chapter>?> {
        chaptersSupplier()
    }

    override var userTags: List<String> by LazyVar<MediaFile, List<String>> {
        usertagsSupplier()
    }

    override fun hashCode(): Int {
        return arrayOf(this::class.qualifiedName!!, path).contentHashCode()
    }

    override fun toString(): String {
        return "MediaFile: $path"
    }

    override fun equals(that: Any?): Boolean {
        if(that !is MediaFile)
            return false
        if(!shallowEquals(that))
            return false

        if(that.chapters != this.chapters
                || that.mediaTags != this.mediaTags
                || that.userTags != this.userTags)
            return false
        //TODO compare all attributes

        return true
    }

    override fun shallowEquals(that: MediaFile?): Boolean {
        if(that === null)
            return false
        return this.type == that.type && this.path == that.path
    }
}
