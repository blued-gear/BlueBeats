package apps.chocolatecakecodes.bluebeats.media.playlist

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile

const val UNDETERMINED_COUNT: Int = -1

/**
 * used to play a playlist by yielding the next-to-play media
 */
internal interface PlaylistIterator {

    /**
     * the number of items in the playlist
     * may return <code>UNDETERMINED_COUNT</code> if the playlist is dynamic
     */
    val totalItems: Int

    /**
     * the current position in the playlist (0-based)
     * may return <code>UNDETERMINED_COUNT</code> if the playlist is dynamic
     */
    var currentPosition: Int

    /**
     * return the next media to play and advances currentPosition by one
     * throw IllegalStateException if the iterator is at its end
     */
    fun nextMedia(): MediaFile

    /**
     * returns true if no more media is available
     */
    fun isAtEnd(): Boolean
}
