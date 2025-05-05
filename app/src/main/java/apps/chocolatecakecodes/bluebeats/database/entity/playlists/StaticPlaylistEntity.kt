package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class StaticPlaylistEntity(
    @PrimaryKey(autoGenerate = false) val id: Long
)
