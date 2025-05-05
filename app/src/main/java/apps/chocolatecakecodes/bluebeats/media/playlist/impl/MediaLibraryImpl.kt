package apps.chocolatecakecodes.bluebeats.media.playlist.impl

import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaLibrary
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import java.io.File

internal class MediaLibraryImpl : MediaLibrary {

    override fun getAllFiles(): Sequence<MediaFile> {
        return RoomDB.DB_INSTANCE.mediaFileDao().getAllFiles().asSequence()
    }

    override fun findFilesWithId3Tag(type: String, value: String): Sequence<MediaFile> {
        return RoomDB.DB_INSTANCE.id3TagDao().getFilesWithTag(type, value).asSequence()
    }

    override fun findFilesWithUsertags(tags: List<String>): Map<MediaFile, List<String>> {
        return RoomDB.DB_INSTANCE.userTagDao().getFilesForTags(tags)
    }

    override fun fileExists(path: String): Boolean {
        return File(path).exists()
    }
}
