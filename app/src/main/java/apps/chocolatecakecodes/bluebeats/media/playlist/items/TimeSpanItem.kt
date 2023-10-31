package apps.chocolatecakecodes.bluebeats.media.playlist.items

import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.media.player.VlcPlayer
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

@Serializable(with = TimeSpanItemSerializer::class)
internal open class TimeSpanItem(
    override val file: MediaFile,
    val startMs: Long,
    val endMs: Long
) : PlaylistItem {//TODO sometimes this skips the item when started

    override fun play(player: VlcPlayer) {
        PlayerController(player).register()
        player.playMedia(file, true)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimeSpanItem) return false

        if (!file.shallowEquals(other.file)) return false
        if (startMs != other.startMs) return false
        if (endMs != other.endMs) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(this.javaClass.name, file, startMs, endMs)
    }

    override fun toString(): String {
        return "TimeSpanItem(file=$file, startMs=$startMs, endMs=$endMs)"
    }

    @Serializable(with = TimeSpanItemInvalidSerializer::class)
    data object INVALID : TimeSpanItem(MediaNode.INVALID_FILE, 0, 0), PlaylistItem.INVALID {
        override fun play(player: VlcPlayer) {
            throw IllegalStateException("INVALID item may not be played")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private inner class PlayerController(
        private val player: VlcPlayer
    ) : TimerThread.TaskRunnable, Player.Listener {

        private var timerId: Int = -1
        private var fileLoaded = false
        private var timeSet = false
        private var justSought = false
        private var cancel = false

        fun register() {
            timerId = TimerThread.INSTANCE.addInterval(100, this)
            player.addListener(this)

            if(file.shallowEquals(player.getCurrentMedia()))
                fileLoaded = true
        }

        override fun invoke(): Long {
            return runBlocking {
                if (fileLoaded) {
                    checkTime()
                }

                if (cancel) {
                    withContext(Dispatchers.Main) { player.removeListener(this@PlayerController) }
                    return@runBlocking -1L
                }

                return@runBlocking 0L
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val currentFile = player.getCurrentMedia()
            if(fileLoaded) {
                if(!file.shallowEquals(currentFile))
                    cancel = true
            } else {
                if(file.shallowEquals(currentFile))
                    fileLoaded = true
            }
        }

        private suspend fun checkTime() {
            withContext(Dispatchers.Main) {
                val time = player.currentPosition

                if(timeSet) {
                    if(justSought) {
                        // wait till player did seek
                        if((time - startMs) < 2000)
                            justSought = false
                    } else {
                        if(time >= endMs) {
                            player.seekToNext()
                            cancel = true
                        } else if(time < startMs) {
                            // the user seems to seeked on their own so stop control of this item
                            cancel = true
                        }
                    }
                } else {
                    player.seekTo(startMs)
                    timeSet = true
                    justSought = true
                }
            }
        }
    }
}

internal class TimeSpanItemSerializer : KSerializer<TimeSpanItem> {

    private val dao = RoomDB.DB_INSTANCE.mediaFileDao()

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(TimeSpanItem::class.qualifiedName!!) {
        element<Long>("fileId")
        element<Long>("startMs")
        element<Long>("endMs")
    }

    override fun serialize(encoder: Encoder, value: TimeSpanItem) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.file.entityId)
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

            if(fileId == MediaNode.INVALID_FILE.entityId)
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
