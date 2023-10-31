package apps.chocolatecakecodes.bluebeats.media.playlist

import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem

/**
 * a playlist where the user puts media in ad-hoc;
 * it can not be saved
 */
internal class TempPlaylist : PlaylistIterator {

    private val items = ArrayList<PlaylistItem>()

    /**
     * creates a TempPlaylist with the given media as the first item
     * @param initialMedia the media to add to the newly created playlist
     */
    constructor(initialMedia: MediaFile) {
        addMedia(initialMedia)
    }

    //region interface methods
    override val totalItems: Int
        get() = items.size
    override var currentPosition: Int = -1
        private set
    override var repeat: Boolean = false
    override var shuffle: Boolean = false
        set(value) {
            field = value

            if(value)
                shuffle()
        }

    override fun nextItem(): PlaylistItem {
        if(isAtEnd())
            throw NoSuchElementException("end reached")

        seek(1)
        return items[currentPosition]
    }

    override fun currentItem(): PlaylistItem {
        return items[currentPosition.coerceAtLeast(0)]
    }

    /**
     * seek relative to the current position
     * (use negative amount for seeking backward)
     *
     * if repeat is true then seeking to one after the last item is allowed;
     *  this will result to reset the iterator to the first item
     *
     * @throws IllegalArgumentException if seeking results in out-of-bounds
     */
    override fun seek(amount: Int) {
        if(amount == 0) return

        val newPos = currentPosition + amount

        if(newPos == totalItems && repeat) {
            currentPosition = 0

            if(shuffle)
                shuffle()
        } else if(newPos >= 0 && newPos < totalItems) {
            currentPosition = newPos
        } else {
            throw IllegalArgumentException("seeking by $amount would result in an out-of-bounds position")
        }
    }

    override fun isAtEnd(): Boolean {
        return !repeat && currentPosition == (totalItems - 1)
    }

    override fun getItems(): List<PlaylistItem> {
        return items
    }
    //endregion

    //region item methods
    fun addMedia(toAdd: MediaFile) {
        addItem(MediaFileItem(toAdd))
    }

    fun addItem(toAdd: PlaylistItem) {
        items.add(toAdd)
    }

    fun removeItem(index: Int) {
        items.removeAt(index)
    }

    fun moveItem(from: Int, newIndex: Int) {
        val item = items.removeAt(from)
        items.add(newIndex, item)
    }
    //endregion

    //region private methods
    private fun shuffle() {
        // current media must stay at same index
        if(currentPosition == -1) {
            items.shuffle()
        } else {
            val media = items.removeAt(currentPosition)
            items.shuffle()
            items.add(currentPosition, media)
        }
    }
    //endregion
}