package apps.chocolatecakecodes.bluebeats.media.playlist

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile

const val UNDETERMINED_COUNT: Int = -1

/**
 * Used to play a playlist by yielding the next-to-play media.
 * A newly initialized iterator should have its currentPosition set before the beginning
 *  (so that the first item can be acquired with nextMedia()).
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
    val currentPosition: Int

    /**
     * returns the next media to play and advances currentPosition by one
     * @throws NoSuchElementException if the iterator is at its end
     */
    fun nextMedia(): MediaFile

    /**
     * returns the current media
     * (no state will be changed)
     */
    fun currentMedia(): MediaFile

    /**
     * seek relative to the current position
     * (use negative amount for seeking backward)
     */
    fun seek(amount: Int)

    /**
     * returns true if no more media is available
     */
    fun isAtEnd(): Boolean
}
