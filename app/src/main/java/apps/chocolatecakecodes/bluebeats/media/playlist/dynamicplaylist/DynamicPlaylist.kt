package apps.chocolatecakecodes.bluebeats.media.playlist.dynamicplaylist

import androidx.room.Dao
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.playlist.Playlist
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistIterator
import apps.chocolatecakecodes.bluebeats.media.playlist.PlaylistType
import apps.chocolatecakecodes.bluebeats.media.playlist.UNDETERMINED_COUNT
import java.util.*
import kotlin.collections.ArrayList

private const val EXAMPLE_ITEM_COUNT = 50

internal class DynamicPlaylist(name: String) : Playlist {

    override val type: PlaylistType = PlaylistType.DYNAMIC

    override var name: String = name
        private set

    val rootRuleGroup = RuleGroup(Rule.Share(1f, true))

    /**
     * the minimum size after which media is allowed to repeat
     * (this determines how large the media-buffer will be before the next items will be generated)
     */
    var iterationSize = EXAMPLE_ITEM_COUNT

    override fun items(): List<MediaFile> {
        return rootRuleGroup.generateItems(iterationSize.coerceAtLeast(EXAMPLE_ITEM_COUNT))
    }

    override fun getIterator(repeat: Boolean, shuffle: Boolean): PlaylistIterator {
        return DynamicPlaylistIterator(rootRuleGroup, iterationSize).apply {
            this.repeat = repeat
            this.shuffle = shuffle
        }
    }

    // DAO as internal class or else some setters would have to be internal
    @Dao
    internal abstract class DynamicPlaylistDAO {
        //TODO
    }
}

internal class DynamicPlaylistIterator(
    private val rootRuleGroup: RuleGroup,
    private val bufferSize: Int
) : PlaylistIterator {

    private val mediaBuffer = ArrayList<MediaFile>(bufferSize + 1)
    private val mediaBufferRO = Collections.unmodifiableList(mediaBuffer)

    override val totalItems: Int = UNDETERMINED_COUNT
    override var currentPosition: Int = -1
        private set

    override var repeat: Boolean = true
    @Suppress("SetterBackingFieldAssignment")
    override var shuffle: Boolean = true
        set(_) {
            // used to trigger regeneration
            generateItems()
        }

    init {
        generateItems()
        currentPosition = -1
    }

    override fun nextMedia(): MediaFile {
        seek(1)
        return mediaBuffer[currentPosition]
    }

    override fun currentMedia(): MediaFile {
        return mediaBuffer[currentPosition.coerceAtLeast(0)]
    }

    override fun seek(amount: Int) {
        if(amount == 0) return

        val newPos = currentPosition + amount

        if(newPos == mediaBuffer.size) {
            generateItems()
        } else if(newPos >= 0 && newPos < totalItems) {
            currentPosition = newPos
        } else {
            throw IllegalArgumentException("seeking by $amount would result in an out-of-bounds position")
        }
    }

    override fun isAtEnd(): Boolean {
        return false
    }

    override fun getItems(): List<MediaFile> {
        return mediaBufferRO
    }

    private fun generateItems() {
        // retain current media (if existing) and place at top
        val currentMedia: MediaFile?
        val toExclude: ExcludeRule
        if(currentPosition >= 0) {
            currentMedia = currentMedia()
            toExclude = ExcludeRule(files = setOf(currentMedia))// exclude to prevent repetition
        } else {
            currentMedia = null
            toExclude = ExcludeRule.EMPTY_EXCLUDE
        }

        mediaBuffer.clear()
        mediaBuffer.addAll(rootRuleGroup.generateItems(bufferSize, toExclude))

        if(currentMedia !== null)
            mediaBuffer.add(0, currentMedia)

        currentPosition = 0
    }
}

//region entities

//endregion
