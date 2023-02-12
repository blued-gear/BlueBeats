package apps.chocolatecakecodes.bluebeats.media.playlist

import androidx.room.*

@Dao
internal abstract class PlaylistsManager {

    fun createNewEntry(name: String, type: PlaylistType, id: Long) {
        insertNewEntry(PlaylistName(0, name, id, type.ordinal))
    }

    fun listAllPlaylist(): Map<String, Pair<PlaylistType, Long>> {
        return getAllPlaylists().associate {
            Pair(it.name, Pair(PlaylistType.values()[it.type], it.playlist))
        }
    }

    fun listAllPlaylistsWithType(type: PlaylistType): Map<String, Long> {
        return getAllPlaylistsWithType(type.ordinal).associate {
            Pair(it.name, it.playlist)
        }
    }

    @Query("DELETE FROM PlaylistName WHERE name = :name;")
    abstract fun deleteEntry(name: String)

    @Query("DELETE FROM PlaylistName WHERE playlist = :id;")
    abstract fun deleteEntry(id: Long)

    @Query("SELECT playlist FROM PlaylistName WHERE name = :name;")
    abstract fun getPlaylistId(name: String): Long

    @Query("SELECT name FROM PlaylistName WHERE playlist = :id;")
    abstract fun getPlaylistName(id: Long): String

    @Query("UPDATE PlaylistName SET name = :newName WHERE name = :oldName;")
    abstract fun renamePlaylist(oldName: String, newName: String)

    @Insert
    protected abstract fun insertNewEntry(entry: PlaylistName)

    @Query("SELECT * FROM PlaylistName;")
    protected abstract fun getAllPlaylists(): List<PlaylistName>

    @Query("SELECT * FROM PlaylistName WHERE type = :type;")
    protected abstract fun getAllPlaylistsWithType(type: Int): List<PlaylistName>
}

@Entity(
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["playlist"], unique = true)
    ]
)
internal data class PlaylistName(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "playlist") val playlist: Long,
    @ColumnInfo(name = "type") val type: Int
)
