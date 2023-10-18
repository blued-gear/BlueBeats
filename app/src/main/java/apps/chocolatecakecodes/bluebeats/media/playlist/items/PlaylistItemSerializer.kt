package apps.chocolatecakecodes.bluebeats.media.playlist.items

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal class PlaylistItemSerializer {

    companion object {
        val INSTANCE: PlaylistItemSerializer by lazy { PlaylistItemSerializer() }

        inline fun deserElements(decoder: CompositeDecoder, descriptor: SerialDescriptor, vararg consumers: () -> Unit) {
            var idx = decoder.decodeElementIndex(descriptor)
            while(idx != CompositeDecoder.DECODE_DONE) {
                consumers[idx]()
                idx = decoder.decodeElementIndex(descriptor)
            }
        }
    }

    private val serializer = Json {
        serializersModule = SerializersModule {
            polymorphic(PlaylistItem::class) {
                subclass(MediaFileItem::class)
                subclass(MediaFileItem.INVALID::class)
            }
        }
    }

    fun serialize(obj: PlaylistItem): String {
        return serializer.encodeToString(obj)
    }

    fun deserialize(json: String): PlaylistItem {
        return serializer.decodeFromString(json)
    }
}
