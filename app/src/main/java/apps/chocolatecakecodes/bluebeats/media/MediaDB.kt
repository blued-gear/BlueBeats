package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.graphics.scale
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaDir
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaFile
import apps.chocolatecakecodes.bluebeats.blueplaylists.interfaces.media.MediaNode
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.database.dao.media.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.media.fileaccess.AndroidFs
import apps.chocolatecakecodes.bluebeats.media.fileaccess.AndroidMediaStore
import apps.chocolatecakecodes.bluebeats.media.model.MediaDirImpl
import apps.chocolatecakecodes.bluebeats.media.model.MediaFileImpl
import apps.chocolatecakecodes.bluebeats.util.castTo
import apps.chocolatecakecodes.bluebeats.util.using
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import java.io.File
import java.io.FileNotFoundException

/**
 * searches media files, extract metadata and index them, store in DB, manage tags
 * actions a synchronous but the progress can be monitored asynchronous by ScanEventHandler
 */
internal class MediaDB(
    private val libVLC: ILibVLC,
    private val eventHandler: ScanEventHandler
) {

    companion object {
        val NOOP_EVENT_HANDLER: ScanEventHandler = object : ScanEventHandler(null) {}

        private const val LOG_TAG = "MediaDB"
        //TODO extend this list
        private val IGNORE_LIST = listOf(
            "db", "nfo", "ini", "jpg", "jpeg", "ljpg", "gif", "png", "pgm", "pgmyuv", "pbm", "pam",
            "tga", "bmp", "pnm", "xpm", "xcf", "pcx", "tif", "tiff", "lbm", "sfv", "txt", "sub",
            "idx", "srt", "ssa", "ass", "smi", "utf", "utf-8", "rt", "aqt", "txt", "usf", "jss",
            "cdg", "psb", "mpsub", "mpl2", "pjs", "dks", "stl", "vtt", "ttml", "pdf"
        ).map { ".$it" }
    }

    private val mediaFactory: IMediaFactory
    private var mediaTree: MediaDirImpl = MediaDirImpl.new(MediaNode.UNSPECIFIED_DIR.id, MediaNode.UNSPECIFIED_DIR.name, { null })

    private val mediaFileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }
    private val mediaDirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }

    private val mediaParser = MediaParser()

    constructor(libVLC: ILibVLC): this(libVLC, NOOP_EVENT_HANDLER){}

    init {
        mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    }

    //region public methods
    fun loadDB() {
        if(mediaDirDao.doesSubdirExist("/", MediaNode.NULL_PARENT_ID)){
            mediaTree = mediaDirDao.getForNameAndParent("/", MediaNode.NULL_PARENT_ID) as MediaDirImpl
        }else{
            mediaTree = mediaDirDao.newDir("/", MediaNode.NULL_PARENT_ID)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scanInAll(ctx: Context) {
        val fileWalker = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            AndroidMediaStore()
        else
            AndroidFs()

        eventHandler.onScanStarted()
        eventHandler.onNewNodeFound(mediaTree)// publish root

        val removedDirs: MutableMap<String, MediaDirImpl> = mediaDirDao.getAllDirs().mapNotNull {
            if(it.parent != null)// exclude root
                it as? MediaDirImpl
            else
                null
        }.associateBy {
            it.path
        }.toMutableMap()
        val removedFiles: MutableMap<String, MediaFileImpl> = mediaFileDao.getAllFiles().associateBy {
            it.path
        }.toMutableMap()

        fun markDirExisting(dir: MediaDir) {
            // mark full dir-path as not removed
            var subHit = false
            var parent: MediaDir? = dir
            while(parent != null) {
                val hit = removedDirs.remove(parent.path) != null

                // If the parent-path was already removed then skip further checking.
                //  As the paths are always checked from bottom to top, once a parent-path
                //  does not hit anymore, the parent should already be removed
                if(subHit) {
                    if(!hit)
                        break
                } else {
                    if(hit)
                        subHit = true
                }

                parent = parent.parent?.castTo()
            }
        }

        val scannerCoroutineCtx = CoroutineScope(newFixedThreadPoolContext(4, "MediaDB scanner"))
        val pipeline: Flow<String> = flow {
            fileWalker.visitAllFiles(ctx) { emit(it) }
        }
        val pipelineJob = scannerCoroutineCtx.launch {
            pipeline.mapNotNull {
                prepareFile(it)
            }.map {
                scannerCoroutineCtx.async {
                    processFile(it)
                }
            }.toList().awaitAll().forEach {
                val file = it.saveAction()

                removedFiles.remove(it.path)
                markDirExisting(file.parent)
            }
        }
        runBlocking { pipelineJob.join() }

        // remove all removed nodes
        removedFiles.values.forEach {
            eventHandler.onNodeRemoved(it)
            it.parent.castTo<MediaDirImpl>().removeFile(it)
            mediaFileDao.delete(it)
        }
        removedDirs.values.forEach {
            eventHandler.onNodeRemoved(it)
            it.parent!!.castTo<MediaDirImpl>().removeDir(it)
            mediaDirDao.delete(it)
        }

        // save all dirs
        fun saveDirRecursively(dir: MediaDir) {
            mediaDirDao.save(dir as MediaDirImpl)

            dir.getDirs().forEach {
                saveDirRecursively(it)
            }

            eventHandler.onNodeProcessed(dir)
        }
        saveDirRecursively(mediaTree)

        eventHandler.onScanFinished()

        runHousekeeping()
    }

    fun getMediaTreeRoot(): MediaDir{
        return mediaTree
    }

    fun fileToMediaFile(path: String): MediaFile {
        val vlcMedia = fileToVlcMedia(path)
        if(vlcMedia === null)
            throw FileNotFoundException("file $path can not be converted to vlc-media")

        return vlcMedia.using(false) {
            mediaParser.parseFile(vlcMedia, MediaNode.UNSPECIFIED_DIR)
        }
    }
    fun fileToVlcMedia(path: String): IMedia? {
        return mediaFactory.getFromLocalPath(libVLC, path)
    }

    fun pathToMedia(path: String): MediaNode {
        var currentParent = mediaTree
        val pathParts = path.split('/')
        pathParts.indices.forEach { i ->
            val pathPart = pathParts[i]
            if (pathPart == "") return@forEach// continue

            val child = currentParent.findChild(pathPart)
                ?: throw FileNotFoundException("path $path not found in media-tree (at $pathPart)")
            when (child) {
                is MediaDirImpl -> {
                    currentParent = child
                }

                is MediaFileImpl -> {
                    if (i != pathParts.size - 1)
                        throw FileNotFoundException("path could not be resolved, because a file was found where a dir was expected (path: $path , current-dir: $pathPart)")
                    return child
                }

                else -> {
                    throw AssertionError("there should be no more subclasses of MediaNode than dir and file")
                }
            }
        }
        return currentParent
    }

    /**
     * @param file the file to generate the thumbnail for
     * @param width the required width; -1 for use aspect-ration from height; if width and height is -1 the image will not be scaled
     * @param height the required height; -1 for use aspect-ration from width; if width and height is -1 the image will not be scaled
     */
    fun getThumbnail(file: MediaFile, width: Int, height: Int): Bitmap? {
        if(!File(file.path).exists())
            return null

        return when(file.type) {
            MediaFile.Type.AUDIO -> {
                tryLoadThumbnail(file.path)?.let {
                    scaleBitmap(it, width, height)
                }
            }
            MediaFile.Type.VIDEO -> {
                (tryLoadThumbnail(file.path) ?: tryLoadVideoFrame(file.path))?.let {
                    scaleBitmap(it, width, height)
                }
            }
            MediaFile.Type.OTHER -> null
        }
    }
    //endregion

    //region private methods
    private fun prepareFile(path: String): ProcessFileInp? {
        if(IGNORE_LIST.any { path.endsWith(it, true) })
            return null

        val lastSlash = path.lastIndexOf('/')
        if(lastSlash == -1 || lastSlash == path.lastIndex)
            return null
        val dirPath = path.substring(0, lastSlash)
        val name = path.substring(lastSlash + 1)

        val dir = mkDirs(dirPath)

        return ProcessFileInp(path, dir, name)
    }

    private fun processFile(inp: ProcessFileInp): ProcessFileOutp {
        val path = inp.path
        val dir = inp.dir

        when(val existingFile = dir.findChild(inp.name)) {
            null -> {
                Log.d(LOG_TAG, "processing new file $path")
                val parsed = fileToVlcMedia(path)!!.using(false) {
                    mediaParser.parseFile(it, dir)
                }

                return ProcessFileOutp(path) {
                    val newFile = mediaFileDao.newFile(parsed)
                    dir.addFile(newFile)

                    eventHandler.onNewNodeFound(newFile)
                    eventHandler.onNodeProcessed(newFile)

                    newFile
                }
            }
            is MediaFile -> {
                Log.d(LOG_TAG, "processing existing file $path")
                val file = existingFile as MediaFileImpl
                val unchangedVersion = mediaFileDao.createCopy(file)
                val updated = updateFile(file)

                val saveAction = if(updated) {
                    {
                        mediaFileDao.save(file)

                        eventHandler.onNodeUpdated(file, unchangedVersion)
                        eventHandler.onNodeProcessed(existingFile)

                        file
                    }
                } else {
                    {
                        eventHandler.onNodeProcessed(existingFile)

                        file
                    }
                }

                return ProcessFileOutp(path, saveAction)
            }
            else -> {
                Log.w(LOG_TAG, "dir with name of exiting file found; removing dir; path: $path")
                Log.d(LOG_TAG, "processing new file (replacing dir) $path")

                val parsed = fileToVlcMedia(path)!!.using(false) {
                    mediaParser.parseFile(it, dir)
                }

                val saveAction = {
                    dir.removeDir(existingFile as MediaDir)

                    val newFile = mediaFileDao.newFile(parsed)
                    dir.addFile(newFile)

                    eventHandler.onNewNodeFound(newFile)
                    eventHandler.onNodeProcessed(newFile)

                    newFile
                }

                return ProcessFileOutp(path, saveAction)
            }
        }
    }

    private fun mkDirs(path: String): MediaDirImpl {
        var parent = mediaTree
        val parts = path.split('/').filter { it.isNotEmpty() }

        for(name in parts) {
            var dir = parent.getDirs().firstOrNull {
                it.name == name
            } as MediaDirImpl?

            if(dir == null) {
                dir = mediaDirDao.newDir(name, parent.id)
                parent.addDir(dir)

                eventHandler.onNewNodeFound(dir)
            }

            parent = dir
        }

        return parent
    }

    private fun updateFile(file: MediaFileImpl): Boolean {
        fileToVlcMedia(file.path)!!.using(false) {
            val fsVersion = mediaParser.parseFile(it, file.parent)
            var wasChanged = false

            if(file.type != fsVersion.type) {
                wasChanged = true
                file.type = fsVersion.type
            }

            if(file.chapters != fsVersion.chapters){
                wasChanged = true
                file.chapters = fsVersion.chapters
            }
            if(!file.mediaTags.laxEquals(fsVersion.mediaTags)){
                wasChanged = true
                file.mediaTags = fsVersion.mediaTags
            }
            if(file.userTags != fsVersion.userTags){
                wasChanged = true
                file.userTags = fsVersion.userTags
            }
            //TODO update more attributes

            return wasChanged
        }
    }

    private fun tryLoadThumbnail(filePath: String): Bitmap? {
        try {
            return useMediaMetadataRetriever { retriever ->
                retriever.setDataSource(filePath)
                retriever.embeddedPicture?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
            }
        } catch(e: Exception) {
            Log.w(LOG_TAG, "unable to extract thumbnail for $filePath", e)
            return null
        }
    }

    private fun tryLoadVideoFrame(filePath: String): Bitmap? {
        val timePercentage = 0.1
        val maxTime = 90000L

        try {
            return useMediaMetadataRetriever { retriever ->
                retriever.setDataSource(filePath)

                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                    ?.let { totalTime ->
                        val thumbTime = (totalTime * timePercentage).toLong().coerceAtMost(maxTime)
                        retriever.getFrameAtTime(
                            thumbTime,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    } ?: return null
            }
        } catch(e: Exception) {
            Log.w(LOG_TAG, "unable to extract frame for thumbnail for $filePath", e)
            return null
        }
    }

    private fun scaleBitmap(src: Bitmap, width: Int, height: Int): Bitmap? {
        if(src.width == 0 || src.height == 0)
            return null

        val sWidth: Int
        val sHeight: Int
        if(width == -1 && height == -1) {
            return src
        } else if(width == -1) {
            sHeight = height
            sWidth = ((src.width.toFloat() / src.height) * sHeight).toInt()
        } else if(height == -1) {
            sWidth = width
            sHeight = ((src.height.toFloat() / src.width) * sWidth).toInt()
        } else {
            sWidth = width
            sHeight = height
        }

        return src.scale(sWidth, sHeight)
    }

    private fun runHousekeeping() {
        RoomDB.DB_INSTANCE.userTagDao().removeOrphanUserTags()
    }

    private inline fun <T> useMediaMetadataRetriever(run: (MediaMetadataRetriever) -> T): T {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaMetadataRetriever().use {
                val ret = run(it)
                it.release()
                ret
            }
        } else {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                return run(retriever)
            } finally {
                if(retriever !== null)
                    retriever.release()
            }
        }
    }
    //endregion

    //region classes

    abstract class ScanEventHandler(private val dispatcher : Handler? = null) {

        internal fun onScanStarted(){
            if(dispatcher !== null){
                dispatcher.post { handleScanStarted() }
            }else{
                handleScanStarted()
            }
        }
        internal fun onScanFinished(){
            if(dispatcher !== null){
                dispatcher.post { handleScanFinished() }
            }else{
                handleScanFinished()
            }
        }
        internal fun onNewNodeFound(node: MediaNode){
            if(dispatcher !== null){
                dispatcher.post { handleNewNodeFound(node) }
            }else{
                handleNewNodeFound(node)
            }
        }
        internal fun onNodeRemoved(node: MediaNode){
            if(dispatcher !== null){
                dispatcher.post { handleNodeRemoved(node) }
            }else{
                handleNodeRemoved(node)
            }
        }
        internal fun onNodeUpdated(node: MediaNode, oldVersion: MediaNode){
            if(dispatcher !== null){
                dispatcher.post { handleNodeUpdated(node, oldVersion) }
            }else{
                handleNodeUpdated(node, oldVersion)
            }
        }
        internal fun onNodeProcessed(node: MediaNode){
            if(dispatcher !== null){
                dispatcher.post { handleNodeProcessed(node) }
            }else{
                handleNodeProcessed(node)
            }
        }
        internal fun onScanException(e: Exception){
            if(dispatcher !== null){
                dispatcher.post { handleScanException(e) }
            }else{
                handleScanException(e)
            }
        }

        protected open fun handleScanStarted(){}
        protected open fun handleScanFinished(){}
        protected open fun handleNewNodeFound(node: MediaNode){}
        protected open fun handleNodeRemoved(node: MediaNode){}
        protected open fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode){}
        protected open fun handleNodeProcessed(node: MediaNode){}
        protected open fun handleScanException(e: Exception){}
    }

    private class ProcessFileInp(val path: String, val dir: MediaDirImpl, val name: String)
    private class ProcessFileOutp(val path: String, val saveAction: () -> MediaFileImpl)
    //endregion
}
