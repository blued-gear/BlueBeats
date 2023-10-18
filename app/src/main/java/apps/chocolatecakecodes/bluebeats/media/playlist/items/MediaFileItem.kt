package apps.chocolatecakecodes.bluebeats.media.playlist.items

import android.util.Log
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.io.File
import java.io.IOException
import java.util.Objects

@Serializable(with = MediaFileItemSerializer::class)
internal open class MediaFileItem(
    override val file: MediaFile
) : PlaylistItem {

    override fun play(player: VlcPlayer) {
        player.playMedia(file, true)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is MediaFileItem) return false

        if(!file.shallowEquals(other.file)) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.name, file)
    }

    override fun toString(): String {
        return "MediaFileItem(file=$file)"
    }

    @Serializable(with = MediaFileItemInvalidSerializer::class)
    data object INVALID : MediaFileItem(MediaNode.INVALID_FILE), PlaylistItem.INVALID {
        override fun play(player: VlcPlayer) {
            throw IllegalStateException("INVALID item may not be played")
        }
    }
}

internal open class MediaFileItemSerializer : KSerializer<MediaFileItem> {

    private val dao = RoomDB.DB_INSTANCE.mediaFileDao()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(MediaFileItem::class.qualifiedName!!) {
        element<Long>("fileId")
    }

    override fun serialize(encoder: Encoder, value: MediaFileItem) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.file.entityId)
        }
    }

    override fun deserialize(decoder: Decoder): MediaFileItem {
        return decoder.decodeStructure(descriptor) {
            var fileId: Long = Long.MIN_VALUE

            PlaylistItemSerializer.deserElements(this, descriptor, {
                fileId = decodeLongElement(descriptor, 0)
            })

            if(fileId == Long.MIN_VALUE)
                throw IOException("deserialization failed (missing fileId)")
            if(fileId == MediaNode.INVALID_FILE.entityId)
                return@decodeStructure MediaFileItem.INVALID

            try {
                val file = dao.getForId(fileId)
                if(!File(file.path).exists())
                    return@decodeStructure MediaFileItem.INVALID

                return@decodeStructure MediaFileItem(file)
            } catch(e: Exception) {
                Log.e("MediaFileItemSerializer", "exception while loading file", e)
                return@decodeStructure MediaFileItem.INVALID
            }
        }
    }
}

internal open class MediaFileItemInvalidSerializer : KSerializer<MediaFileItem.INVALID> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(MediaFileItem.INVALID::class.qualifiedName!!) {
        element<Long>("fileId")
    }

    override fun serialize(encoder: Encoder, value: MediaFileItem.INVALID) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.file.entityId)
        }
    }

    override fun deserialize(decoder: Decoder): MediaFileItem.INVALID {
        return decoder.decodeStructure(descriptor) {
            // just consume data
            PlaylistItemSerializer.deserElements(this, descriptor, {
                decodeLongElement(descriptor, 0)
            })

            MediaFileItem.INVALID
        }
    }
}
