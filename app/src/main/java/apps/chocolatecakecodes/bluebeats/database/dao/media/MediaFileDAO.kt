package apps.chocolatecakecodes.bluebeats.database.dao.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.blueplaylists.model.tag.Chapter
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.entity.media.MediaFileEntity
import apps.chocolatecakecodes.bluebeats.media.model.MediaDirImpl
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileImpl
import apps.chocolatecakecodes.bluebeats.util.TimerThread
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.util.concurrent.TimeUnit

@Dao
internal abstract class MediaFileDAO{

    private val cache: Cache<Long, MediaFileImpl>
    private val chaptersSerializer: Json

    private val mediaDirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }
    private val id3TagDao: ID3TagDAO by lazy {
        RoomDB.DB_INSTANCE.id3TagDao()
    }
    private val userTagDao: UserTagsDAO by lazy {
        RoomDB.DB_INSTANCE.userTagDao()
    }

    init{
        cache = CacheBuilder.newBuilder().weakValues().build()
        TimerThread.INSTANCE.addInterval(TimeUnit.MINUTES.toMillis(5)) {
            cache.cleanUp()
            0
        }

        chaptersSerializer = Json {
            this.serializersModule = SerializersModule {
                this.contextual(Chapter::class, ChapterSerializer())
            }
        }
    }

    //region public methods
    fun createCopy(from: MediaFileImpl): MediaFileImpl {
        val mediaTagsCpy = from.mediaTags.copy()
        val chaptersCpy = from.chapters
        val usertagsCpy = from.userTags

        return MediaFileImpl.new(
            MediaNode.UNALLOCATED_NODE_ID,
            from.name,
            from.type,
            { from.parent },
            { mediaTagsCpy },
            { chaptersCpy },
            { usertagsCpy }
        )
    }

    @Transaction
    open fun newFile(name: String, type: MediaFile.Type, parent: Long): MediaFileImpl {
        val fileEntity = MediaFileEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent, type, null)
        val id = insertEntity(fileEntity)

        return getForId(id)
    }

    @Transaction
    open fun newFile(from: MediaFileImpl): MediaFileImpl{
        val newFile = newFile(from.name, from.type, from.parent.id)

        // copy extra attributes (TODO update whenever attributes changes)
        newFile.mediaTags = from.mediaTags.copy()
        newFile.userTags = from.userTags
        newFile.chapters = from.chapters
        save(newFile)

        return newFile
    }

    fun getForId(id: Long): MediaFileImpl{
        return cache.get(id) {
            val entity = getEntityForId(id)

            val parentSupplier = {
                mediaDirDao.getForId(entity.parent)
            }
            val mediaTagsSupplier = {
                RoomDB.DB_INSTANCE.id3TagDao().getTagsOfFile(id)
            }
            val chaptersJson = entity.chaptersJson
            val chaptersSupplier: () -> List<Chapter>? = {
                if(chaptersJson.isNullOrEmpty())
                    null
                else
                    chaptersSerializer.decodeFromString<List<Chapter>>(chaptersJson)
            }
            val usertagsSupplier = {
                if(entity.id == MediaNode.UNALLOCATED_NODE_ID)// can not load tags from db if this file is not saved
                    emptyList()
                else
                    RoomDB.DB_INSTANCE.userTagDao().getUserTagsForFile(id)
            }

            MediaFileImpl.new(
                id,
                entity.name,
                entity.type,
                parentSupplier,
                mediaTagsSupplier, chaptersSupplier, usertagsSupplier
            )
        }
    }

    fun getFilesInDir(parent: MediaDirImpl): List<MediaFileImpl>{
        return getFileIdsInDir(parent.id).map { getForId(it) }
    }

    fun getForNameAndParent(name: String, parent: Long): MediaFileImpl{
        return getForId(getFileIdForNameAndParent(name, parent))
    }

    fun getAllFiles(): List<MediaFileImpl> {
        return getAllIds().map {
            getForId(it)
        }
    }

    @Transaction
    open fun save(file: MediaFileImpl){
        val chaptersJson = file.chapters?.let {
            chaptersSerializer.encodeToString(it)
        }

        updateEntity(
            MediaFileEntity(
                file.id,
                file.name,
                file.parent.id,
                file.type,
                chaptersJson
            )
        )

        userTagDao.saveUserTagsOfFile(file, file.userTags)
        id3TagDao.saveTagOfFile(file)
    }

    @Transaction
    open fun delete(file: MediaFileImpl){
        userTagDao.deleteUserTagsOfFile(file)
        id3TagDao.deleteAllEntriesOfFile(file)
        deleteEntity(file.id)
        cache.invalidate(file.id)
    }

    @Query("SELECT EXISTS(SELECT id FROM MediaFileEntity WHERE id = :id);")
    abstract fun doesFileExist(id: Long): Boolean

    @Query("SELECT EXISTS(SELECT id FROM MediaFileEntity WHERE name = :name AND parent = :parent);")
    abstract fun doesFileInDirExist(name: String, parent: Long): Boolean
    //endregion

    //region db actions
    @Query("SELECT * FROM MediaFileEntity WHERE id = :id;")
    protected abstract fun getEntityForId(id: Long): MediaFileEntity

    @Query("SELECT id FROM MediaFileEntity WHERE parent = :parent;")
    protected abstract fun getFileIdsInDir(parent: Long): List<Long>

    @Query("SELECT id FROM MediaFileEntity WHERE name = :name AND parent = :parent;")
    protected abstract fun getFileIdForNameAndParent(name: String, parent: Long): Long

    @Query("SELECT id FROM MediaFileEntity;")
    protected abstract fun getAllIds(): List<Long>

    @Insert
    protected abstract fun insertEntity(entity: MediaFileEntity): Long

    @Update
    protected abstract fun updateEntity(entity: MediaFileEntity)

    @Query("DELETE FROM MediaFileEntity WHERE id = :id;")
    protected abstract fun deleteEntity(id: Long)
    //endregion

    private class ChapterSerializer : KSerializer<Chapter> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MediaFileDao::ChapterSerializer") {
            this.element<Long>("start")
            this.element<Long>("end")
            this.element<String>("name")
        }

        override fun serialize(encoder: Encoder, value: Chapter) {
            encoder.encodeStructure(descriptor) {
                this.encodeLongElement(descriptor, 0, value.start)
                this.encodeLongElement(descriptor, 1, value.end)
                this.encodeStringElement(descriptor, 2, value.name)
            }
        }

        override fun deserialize(decoder: Decoder): Chapter {
            return decoder.decodeStructure(descriptor) {
                var start: Long = -1
                var end: Long = -1
                var name: String = ""

                while(true) {
                    when(this.decodeElementIndex(descriptor)) {
                        0 -> start = this.decodeLongElement(descriptor, 0)
                        1 -> end = this.decodeLongElement(descriptor, 1)
                        2 -> name = this.decodeStringElement(descriptor, 2)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> throw SerializationException("unexpected element in Chapter")
                    }
                }

                if(start == -1L)
                    throw SerializationException("missing 'start' for Chapter")
                if(end == -1L)
                    throw SerializationException("missing 'end' for Chapter")

                return@decodeStructure Chapter(start, end, name)
            }
        }
    }
}
