package apps.chocolatecakecodes.bluebeats.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.util.Log
import apps.chocolatecakecodes.bluebeats.database.MediaDirDAO
import apps.chocolatecakecodes.bluebeats.database.MediaFileDAO
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaDir
import apps.chocolatecakecodes.bluebeats.media.model.MediaFile
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import apps.chocolatecakecodes.bluebeats.taglib.TagParser
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.using
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import java.io.FileNotFoundException
import java.io.IOException

/**
 * first: subdirs, second: files
 */
private typealias DirContents = Pair<List<IMedia>, List<IMedia>>

/**
 * searches media files, extract metadata and index them, store in DB, manage tags
 * actions a synchronous but the progress can be monitored asynchronous by ScanEventHandler
 */
internal class MediaDB constructor(private val libVLC: ILibVLC, private val eventHandler: ScanEventHandler){

    companion object {
        val NOOP_EVENT_HANDLER: ScanEventHandler = object : ScanEventHandler(null) {}

        private const val LOG_TAG = "MediaDB"
        //TODO extend this list
        private const val IGNORE_LIST_OPTION = ":ignore-filetypes=db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,ssa,ass,smi,utf,utf-8,rt,aqt,txt,usf,jss,cdg,psb,mpsub,mpl2,pjs,dks,stl,vtt,ttml,pdf"
    }

    private val mediaFactory: IMediaFactory
    private val scanRoots: MutableSet<String>
    private var mediaTree: MediaDir = MediaNode.UNSPECIFIED_DIR

    private val mediaFileDao: MediaFileDAO by lazy {
        RoomDB.DB_INSTANCE.mediaFileDao()
    }
    private val mediaDirDao: MediaDirDAO by lazy {
        RoomDB.DB_INSTANCE.mediaDirDao()
    }

    constructor(libVLC: ILibVLC): this(libVLC, NOOP_EVENT_HANDLER){}

    init{
        mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
        scanRoots = HashSet()
    }

    //region public methods

    fun addScanRoot(path: String){
        scanRoots.add(path)
    }
    fun removeScanRoot(path: String){
        scanRoots.remove(path)
    }

    fun loadDB(){
        if(mediaDirDao.doesSubdirExist("/", MediaNode.NULL_PARENT_ID)){
            mediaTree = mediaDirDao.getForNameAndParent("/", MediaNode.NULL_PARENT_ID)
        }else{
            mediaTree = mediaDirDao.newDir("/", MediaNode.NULL_PARENT_ID)
        }
    }

    fun scanInAll() {
        eventHandler.onScanStarted()
        eventHandler.onNewNodeFound(mediaTree)// publish root

        checkForDeletedScanRoots().forEach {
            mediaTree.removeDir(it)
            mediaDirDao.delete(it)
            eventHandler.onNodeRemoved(it)
        }

        for(scanRoot in scanRoots){
            try {
                var currentParent: MediaDir = mediaTree
                val pathParts = scanRoot.split('/')
                for(part in pathParts){
                    // create dir-struct from root to scanRoot
                    if(part == "") continue
                    var nextParent = currentParent.findChild(part) as? MediaDir
                    if(nextParent === null){
                        nextParent = mediaDirDao.newDir(part, currentParent.entityId)
                        currentParent.addDir(nextParent)
                    }
                    currentParent = nextParent
                    eventHandler.onNewNodeFound(currentParent)
                }

                updateDirDeep(currentParent)
            }catch (e: Exception){
                eventHandler.onScanException(e)
            }
        }

        eventHandler.onScanFinished()
    }

    fun getMediaTreeRoot(): MediaDir{
        return mediaTree
    }

    fun fileToMediaFile(path: String): MediaFile{
        val vlcMedia = fileToVlcMedia(path)
        if(vlcMedia === null)
            throw FileNotFoundException("file $path can not be converted to vlc-media")

        vlcMedia.using(false){
            return parseFile(vlcMedia, MediaNode.UNSPECIFIED_DIR)
        }
        throw AssertionError("unreachable")
    }
    fun fileToVlcMedia(path: String): IMedia?{
        return mediaFactory.getFromLocalPath(libVLC, path)
    }

    fun pathToMedia(path: String, detached: Boolean = false): MediaNode{
        if(detached){
            val media = fileToVlcMedia(path)
                ?: throw FileNotFoundException("path $path could not be converted to a vlc-media")
            media.using(false){
                media.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

                if(media.type == IMedia.Type.Directory){
                    val dir = MediaDir.new(
                        MediaNode.UNSPECIFIED_DIR.entityId,
                        media.uri.lastPathSegment!!,
                        { null }
                    )

                    val contents = scanDir(media)
                    for(subdir in contents.first){
                        dir.addDir(MediaDir.new(
                            MediaNode.UNSPECIFIED_DIR.entityId,
                            subdir.uri.lastPathSegment!!,
                            { mediaDirDao.getForId(dir.entityId) }
                        ))
                    }
                    for(file in contents.second){
                        dir.addFile(parseFile(file, dir))
                    }

                    return dir
                }else if(media.type == IMedia.Type.File){
                    return parseFile(media, MediaNode.UNSPECIFIED_DIR)
                }else{
                    throw IllegalArgumentException("path is not a file nor a directory")
                }
            }
        }else{
            var currentParent = mediaTree
            val pathParts = path.split('/')
            pathParts.indices.forEach { i ->
                val pathPart = pathParts[i]
                if (pathPart == "") return@forEach// continue

                val child = currentParent.findChild(pathPart)
                    ?: throw FileNotFoundException("path $path not found in media-tree (at $pathPart)")
                if(child is MediaDir){
                    currentParent = child
                }else if (child is MediaFile){
                    if (i != pathParts.size - 1)
                        throw FileNotFoundException("path could not be resolved, because a file was found where a dir was expected (path: $path , current-dir: $pathPart)")
                    return child
                }else{
                    throw AssertionError("there should be no more subclasses of MediaNode than dir and file")
                }
            }
            return currentParent
        }

        throw AssertionError("unreachable")
    }

    @SuppressLint("Range")
    fun getThumbnail(file: MediaFile, width: Int, height: Int): Bitmap? {
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

    /**
     * @param dir parsed dir which should be scanned
     * @return parsed subdirs and files in the given dir
     */
    private fun scanDir(dir: IMedia): DirContents{
        if(!Utils.vlcMediaToDocumentFile(dir).isDirectory)
            throw IllegalArgumentException("path is not a directory")

        val dirs = ArrayList<IMedia>()
        val files = ArrayList<IMedia>()

        dir.using {
            if(!dir.isParsed) {
                dir.addOption(IGNORE_LIST_OPTION)
                dir.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)
            }

            dir.subItems().using(false) {
                for(i in 0 until it.count){
                    it.getMediaAt(i).using(false) { child ->
                        child.addOption(IGNORE_LIST_OPTION)
                        child.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

                        when (child.type) {
                            IMedia.Type.Directory -> {
                                assert(Utils.vlcMediaToDocumentFile(child).isDirectory)
                                dirs.add(child)
                            }
                            IMedia.Type.File -> {
                                assert(Utils.vlcMediaToDocumentFile(child).isFile)
                                files.add(child)
                            }
                            else -> {
                                Log.d(LOG_TAG, "unknown media type encountered: ${child.type}  -  ${child.uri}")
                            }
                        }
                    }
                }
            }
        }

        return Pair(dirs, files)
    }

    private fun updateDirDeep(dir: MediaDir){
        val vlcDir = fileToVlcMedia(dir.path) ?: throw IOException("unable to create media")
        vlcDir.using(false) {
            // scan top level
            val dirContents = scanDir(vlcDir)

            // update top level
            updateDir(dir, dirContents)

            // process sub-dirs
            for(subDir in dir.getDirs()){
                updateDirDeep(subDir)
            }
        }
    }

    private fun updateDir(dir: MediaDir, contents: DirContents){//TODO should a onNodeUpdated be fired if the dir-content changes (new / removed files / dirs)?
        var wasChanged = false

        // check dirs
        val discoveredSubdirNames = contents.first.map { it.uri.lastPathSegment!! }.toSet()
        val existingSubdirsWithName = mapOf(*dir.getDirs().map { Pair(it.name, it) }.toTypedArray())

        Utils.diffChanges(existingSubdirsWithName.keys, discoveredSubdirNames).let { (addedDirs, deletedDirs, _) ->
            // add new subdirs
            addedDirs.forEach {
                wasChanged = true
                val newSubdir = mediaDirDao.newDir(it, dir.entityId)
                dir.addDir(newSubdir)
                eventHandler.onNewNodeFound(newSubdir)
            }

            // delete old subdirs
            deletedDirs.forEach{
                wasChanged = true
                val child = existingSubdirsWithName[it]!!
                mediaDirDao.delete(child)
                dir.removeDir(child)
                eventHandler.onNodeRemoved(child)
            }
        }

        // check files
        val discoveredFilesWithName = mapOf(*contents.second.map { Pair(it.uri.lastPathSegment!!, it) }.toTypedArray())
        val existingFilesWithName = mapOf(*dir.getFiles().map{ Pair(it.name, it) }.toTypedArray())

        Utils.diffChanges(existingFilesWithName.keys, discoveredFilesWithName.keys).let { (addedFiles, deletedFiles, existingFiles) ->
            // add new files
            addedFiles.forEach {
                wasChanged = true
                val fileMedia = discoveredFilesWithName[it]!!
                val newFile = mediaFileDao.newFile(parseFile(fileMedia, dir))
                dir.addFile(newFile)
                eventHandler.onNewNodeFound(newFile)
                eventHandler.onNodeProcessed(newFile)
            }

            // remove old files
            deletedFiles.forEach {
                wasChanged = true
                val child = existingFilesWithName[it]!!
                dir.removeFile(child)
                mediaFileDao.delete(child)
                eventHandler.onNodeRemoved(child)
            }

            // update files (do not re-check added files as they were just parsed)
            existingFiles.forEach {
                val child = existingFilesWithName[it]!!
                updateFile(child)
                eventHandler.onNodeProcessed(child)
            }
        }

        if(wasChanged)
            mediaDirDao.save(dir)
        eventHandler.onNodeProcessed(dir)
    }

    private fun parseFile(file: IMedia, parent: MediaDir): MediaFile{
        assert(file.type == IMedia.Type.File)

        val name = file.uri.lastPathSegment ?: throw IllegalArgumentException("media has invalid path")

        if(!file.isParsed)
            file.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

        // parse type
        var type = MediaFile.Type.OTHER
        for(i in 0 until file.trackCount) {
            if (file.getTrack(i).type == IMedia.Track.Type.Video) {
                type = MediaFile.Type.VIDEO
                break
            }
        }
        if(type === MediaFile.Type.OTHER){
            for (i in 0 until file.trackCount) {
                if (file.getTrack(i).type == IMedia.Track.Type.Audio) {
                    type = MediaFile.Type.AUDIO
                    break
                }
            }
        }

        val mf = MediaFile.new(
            MediaNode.UNALLOCATED_NODE_ID,
            name,
            type,
            { mediaDirDao.getForId(parent.entityId) }
        )

        parseTags(mf)

        //TODO parse more attributes

        return mf
    }

    private fun updateFile(file: MediaFile){
        fileToVlcMedia(file.path)!!.using(false){
            val fsVersion = parseFile(it, file.parent)
            val unchangedVersion = mediaFileDao.createCopy(file)
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

            if(wasChanged){
                mediaFileDao.save(file)
                eventHandler.onNodeUpdated(file, unchangedVersion)
            }
        }
    }

    private fun parseTags(file: MediaFile){
        if(file.type == MediaFile.Type.AUDIO) {
            try {
                val parser = TagParser(file.path)
                parser.parse()

                file.mediaTags = parser.tagFields
                file.userTags = parser.userTags?.tags ?: emptyList()
                file.chapters = parser.chapters
            }catch (e: Exception){
                Log.d(LOG_TAG, "exception in parser; file: ${file.path}", e)
            }
        }
    }

    private fun tryLoadThumbnail(filePath: String): Bitmap? {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(filePath)
            retriever.embeddedPicture?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        }
    }

    private fun tryLoadVideoFrame(filePath: String): Bitmap? {
        val timePercentage = 0.1
        val maxTime = 90000L

        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(filePath)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.let { totalTime ->
                val thumbTime = (totalTime * timePercentage).toLong().coerceAtMost(maxTime)
                retriever.getFrameAtTime(thumbTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null
        }
    }

    private fun scaleBitmap(src: Bitmap, width: Int, height: Int): Bitmap? {
        if(src.width == 0 || src.height == 0)
            return null

        val sWidth: Int
        val sHeight: Int
        if(width == -1 && height == -1) {
            sWidth = src.width
            sHeight = src.height
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

        return Bitmap.createScaledBitmap(src, sWidth, sHeight, true)
    }

    private fun checkForDeletedScanRoots(): Set<MediaDir> {
        class PathPart(val part: String) {
            val next = ArrayList<PathPart>()
            var isTerminator = false

            fun addSub(name: String): PathPart {
                return next.find {
                    it.part == name
                } ?: run {
                    PathPart(name).also {
                        next.add(it)
                    }
                }
            }
        }

        fun checkDir(dir: MediaDir, currentPathPart: PathPart): Set<MediaDir> {
            if(currentPathPart.isTerminator) {
                return if(dir.name == currentPathPart.part)
                    emptySet()
                else
                    setOf(dir)
            }

            return dir.getDirs().fold(emptySet()) { acc, cur ->
                acc + let {
                    currentPathPart.next.find {
                        it.part == cur.name
                    }?.let {
                        checkDir(cur, it)
                    } ?: setOf(cur)
                }
            }
        }

        val rootParts = PathPart("/")
        scanRoots.forEach {
            it.split('/').filter {
                it.isNotEmpty()
            }.fold(rootParts) { acc, cur ->
               acc.addSub(cur)
            }.also {
                it.isTerminator = true
            }
        }

        return checkDir(getMediaTreeRoot(), rootParts)
    }
    //endregion

    //region classes

    abstract class ScanEventHandler(private val dispatcher : Handler? = null){

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
    //endregion
}
