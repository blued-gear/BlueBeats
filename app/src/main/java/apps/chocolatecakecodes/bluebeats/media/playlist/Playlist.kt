package apps.chocolatecakecodes.bluebeats.media.playlist

import apps.chocolatecakecodes.bluebeats.media.playlist.items.PlaylistItem

/**
 * provides a generic interface for the most important playlist-attributes
 */
internal interface Playlist {

    /**
     * the type of the playlist
     */
    val type: PlaylistType

    /**
     * the name of the playlist
     * (may be changed per DAO)
     */
    val name: String

    /**
     * returns all items in this playlist
     * (in case of dynamic playlists this may only be a representative selection)
     */
    fun items(): List<PlaylistItem>

    /**
     * generates a PlaylistIterator to play this playlist with the given parameters
     * @param repeat if the playlist should play endlessly
     * @param shuffle if the playlist should be shuffled
     */
    fun getIterator(repeat: Boolean, shuffle: Boolean): PlaylistIterator
}

enum class PlaylistType {

    /**
     * type of playlist where all media was added by the user
     */
    STATIC,

    /**
     * type of playlist where the list of next media is generated based on rules at every play-through
     */
    DYNAMIC
}
