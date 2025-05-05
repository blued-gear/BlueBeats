package apps.chocolatecakecodes.bluebeats.database.entity.media

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ID3TagValueEntity::class,
            parentColumns = ["id"], childColumns = ["tag"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"], childColumns = ["file"],
            onUpdate = ForeignKey.RESTRICT, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tag", "file"], unique = true),
        Index(value = ["file"])
    ]
)
internal data class ID3TagReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val tag: Long,
    val file: Long
)
