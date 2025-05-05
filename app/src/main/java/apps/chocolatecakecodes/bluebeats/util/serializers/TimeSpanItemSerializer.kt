package apps.chocolatecakecodes.bluebeats.util.serializers

import android.util.Log
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.playlist.items.TimeSpanItem
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

internal class TimeSpanItemSerializer : KSerializer<TimeSpanItem> {

    private val dao = RoomDB.DB_INSTANCE.mediaFileDao()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(TimeSpanItem::class.qualifiedName!!) {
        element<Long>("fileId")
        element<Long>("startMs")
        element<Long>("endMs")
    }

    override fun serialize(encoder: Encoder, value: TimeSpanItem) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.file.id)
            encodeLongElement(descriptor, 1, value.startMs)
            encodeLongElement(descriptor, 2, value.endMs)
        }
    }

    override fun deserialize(decoder: Decoder): TimeSpanItem {
        return decoder.decodeStructure(descriptor) {
            var fileId: Long = Long.MIN_VALUE
            var startMs: Long = Long.MIN_VALUE
            var endMs: Long = Long.MIN_VALUE

            PlaylistItemSerializer.deserElements(this, descriptor,
                { fileId = decodeLongElement(descriptor, 0) },
                { startMs = decodeLongElement(descriptor, 1) },
                { endMs = decodeLongElement(descriptor, 2) }
            )

            if(fileId == Long.MIN_VALUE)
                throw IOException("deserialization failed (missing fileId)")
            if(startMs == Long.MIN_VALUE)
                throw IOException("deserialization failed (missing startMs)")
            if(endMs == Long.MIN_VALUE)
                throw IOException("deserialization failed (missing endMs)")

            if(fileId == MediaNode.INVALID_FILE.id)
                return@decodeStructure TimeSpanItem.INVALID

            try {
                val file = dao.getForId(fileId)
                if(!File(file.path).exists())
                    return@decodeStructure TimeSpanItem.INVALID

                return@decodeStructure TimeSpanItem(file, startMs, endMs)
            } catch(e: Exception) {
                Log.e("MediaFileItemSerializer", "exception while loading file", e)
                return@decodeStructure TimeSpanItem.INVALID
            }
        }
    }
}

internal class TimeSpanItemInvalidSerializer : KSerializer<TimeSpanItem.INVALID> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(TimeSpanItem.INVALID::class.qualifiedName!!)

    override fun serialize(encoder: Encoder, value: TimeSpanItem.INVALID) {
        encoder.encodeStructure(descriptor) {}
    }

    override fun deserialize(decoder: Decoder): TimeSpanItem.INVALID {
        return decoder.decodeStructure(descriptor) {
            // just consume data
            PlaylistItemSerializer.deserElements(this, descriptor)

            TimeSpanItem.INVALID
        }
    }
}
