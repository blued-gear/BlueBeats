package apps.chocolatecakecodes.bluebeats.database.entity.playlists

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = StaticPlaylistEntity::class,
            parentColumns = ["id"], childColumns = ["playlist"],
            onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["playlist"])
    ]
)
internal data class StaticPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "playlist") val playlist: Long,
    @ColumnInfo(name = "item") val item: String,
    @ColumnInfo(name = "pos") val pos: Int
)
