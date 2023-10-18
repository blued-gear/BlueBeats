package apps.chocolatecakecodes.bluebeats.media.playlist.items

import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
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
import java.io.IOException

@Serializable(with = MediaFileItemSerializer::class)
internal data class MediaFileItem(
    override val file: MediaFile
) : PlaylistItem {

    override fun play(player: VlcPlayer) {
        player.playMedia(file, true)
    }
}

internal class MediaFileItemSerializer : KSerializer<MediaFileItem> {

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
            var fileId: Long = -1

            PlaylistItemSerializer.deserElements(this, descriptor, {
                fileId = decodeLongElement(descriptor, 0)
            })

            if(fileId == -1L)
                throw IOException("deserialization failed (missing fileId)")

            val file = dao.getForId(fileId)
            MediaFileItem(file)
        }
    }
}
