package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile

@Entity(
    foreignKeys = [ForeignKey(entity = MediaDirEntity::class,
        parentColumns = ["id"], childColumns = ["parent"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT)],
    indices = [Index(value = ["name", "parent"], unique = true)]
)
internal data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "name", index = true) val name: String,
    @ColumnInfo(name = "parent", index = true) val parent: Long,
    @ColumnInfo(name = "type", index = true) var type: MediaFile.Type,

    @ColumnInfo(name = "chapters", index = false)
    var chaptersJson: String?// store them as JSON because this is the easiest way to store lists
)
