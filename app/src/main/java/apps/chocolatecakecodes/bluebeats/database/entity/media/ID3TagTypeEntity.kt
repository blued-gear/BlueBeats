package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["str"], unique = true)]
)
internal data class ID3TagTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val str: String
)
