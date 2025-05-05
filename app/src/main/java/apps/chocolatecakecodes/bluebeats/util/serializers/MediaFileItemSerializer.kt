package apps.chocolatecakecodes.bluebeats.util.serializers

import android.util.Log
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.MediaFileItem
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.io.File
import java.io.IOException

internal open class MediaFileItemSerializer : KSerializer<MediaFileItem> {

    private val dao = RoomDB.DB_INSTANCE.mediaFileDao()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(MediaFileItem::class.qualifiedName!!) {
        element<Long>("fileId")
    }

    override fun serialize(encoder: Encoder, value: MediaFileItem) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.file.id)
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
            if(fileId == MediaNode.INVALID_FILE.id)
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
            encodeLongElement(descriptor, 0, value.file.id)
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
