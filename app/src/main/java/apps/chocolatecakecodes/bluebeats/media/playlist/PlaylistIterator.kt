package apps.chocolatecakecodes.bluebeats.media.playlist

import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem

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
     * if true the iterator will automatically reset at the end of its list
     * (nextMedia() will always be successful)
     */
    var repeat: RepeatMode

    /**
     * if true the items will be shuffled every time the iterator is reset
     * (additionally it will shuffle whenever this value is set to true)
     */
    var shuffle: Boolean

    /**
     * returns the next media to play and advances currentPosition by one
     * @throws NoSuchElementException if the iterator is at its end
     */
    fun nextItem(): PlaylistItem

    /**
     * returns the current media
     * (no state will be changed)
     */
    fun currentItem(): PlaylistItem

    /**
     * seek relative to the current position
     * (use negative amount for seeking backward)
     */
    fun seek(amount: Int)

    /**
     * returns true if no more media is available
     */
    fun isAtEnd(): Boolean

    /**
     * Returns the list of all items in this iterator.
     * If shuffle is true then this method will return the shuffled items.
     * (In case of a dynamic playlist the result is the current collection before the next items will be generated.)
     */
    fun getItems(): List<PlaylistItem>

    enum class RepeatMode {
        NONE, ALL, ONE
    }
}
