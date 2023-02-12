package apps.chocolatecakecodes.bluebeats.media

import android.os.Handler
import android.util.Log
import apps.chocolatecakecodes.bluebeats.database.RoomDB
import apps.chocolatecakecodes.bluebeats.media.model.*
import apps.chocolatecakecodes.bluebeats.taglib.TagFields
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
class MediaDB constructor(private val libVLC: ILibVLC, private val eventHandler: ScanEventHandler){

    companion object {
        val NOOP_EVENT_HANDLER: ScanEventHandler = object : ScanEventHandler(null) {}

        //TODO extend this list
        private const val IGNORE_LIST_OPTION = ":ignore-filetypes=db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,ssa,ass,smi,utf,utf-8,rt,aqt,txt,usf,jss,cdg,psb,mpsub,mpl2,pjs,dks,stl,vtt,ttml,pdf"
    }

    private val mediaFactory: IMediaFactory
    private val scanRoots: MutableSet<String>
    private var mediaTree: MediaDir = MediaNode.UNSPECIFIED_DIR
    //TODO collections that maps files to types, tags, ...

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
        val dirDao = RoomDB.DB_INSTANCE.mediaDirDao()
        if(dirDao.doesSubdirExist("/", MediaNode.NULL_PARENT_ID)){
            mediaTree = dirDao.getForNameAndParent("/", MediaNode.NULL_PARENT_ID)
        }else{
            mediaTree = dirDao.newDir("/", MediaNode.NULL_PARENT_ID)
        }
    }

    fun scanInAll() {
        val dirDao = RoomDB.DB_INSTANCE.mediaDirDao()

        eventHandler.onScanStarted()
        eventHandler.onNewNodeFound(mediaTree)// publish root

        for(scanRoot in scanRoots){
            try {
                var currentParent: MediaDir = mediaTree
                val pathParts = scanRoot.split('/')
                for(part in pathParts){
                    // create dir-struct from root to scanRoot
                    if(part == "") continue
                    currentParent = (currentParent.findChild(part) as? MediaDir)
                        ?: dirDao.newDir(part, currentParent.entity.id)
                    eventHandler.onNewNodeFound(currentParent)
                }

                updateDirDeep(currentParent)
            }catch (e: Exception){
                eventHandler.onScanException(e)
            }
        }

        // check for deleted roots
        mediaTree.getDirs().map { it.name }.toHashSet().let { dirNames ->
            mediaTree.getDirs().filter { !dirNames.contains(it.name) }.forEach {
                mediaTree.removeDir(it)
                dirDao.delete(it)
                eventHandler.onNodeRemoved(it)
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
                    val dir = MediaDir(MediaDirEntity(MediaNode.UNSPECIFIED_DIR.entity.id, media.uri.lastPathSegment!!, MediaNode.NULL_PARENT_ID))

                    val contents = scanDir(media)
                    for(subdir in contents.first){
                        dir.addDir(MediaDir(MediaDirEntity(MediaNode.UNSPECIFIED_DIR.entity.id, subdir.uri.lastPathSegment!!, dir.entity.id)))
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

            for(i in 0 until dir.subItems().count){
                val child = dir.subItems().getMediaAt(i)
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
                        Log.d("MediaDB", "unknown media type encountered: ${child.type}  -  ${child.uri}")
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
        val dirDao = RoomDB.DB_INSTANCE.mediaDirDao()
        val fileDao = RoomDB.DB_INSTANCE.mediaFileDao()
        var wasChanged = false

        // check dirs
        val discoveredSubdirNames = contents.first.map { it.uri.lastPathSegment!! }.toSet()
        val existingSubdirsWithName = mapOf(*dir.getDirs().map { Pair(it.name, it) }.toTypedArray())
        // add new subdirs
        discoveredSubdirNames.minus(existingSubdirsWithName.keys).forEach {
            wasChanged = true
            val newSubdir = dirDao.newDir(it, dir.entity.id)
            dir.addDir(newSubdir)
            eventHandler.onNewNodeFound(newSubdir)
        }
        // delete old subdirs
        existingSubdirsWithName.keys.minus(discoveredSubdirNames).forEach{
            wasChanged = true
            val child = existingSubdirsWithName[it]!!
            dirDao.delete(child)
            dir.removeDir(child)
            eventHandler.onNodeRemoved(child)
        }

        // check files
        val discoveredFilesWithName = mapOf(*contents.second.map { Pair(it.uri.lastPathSegment!!, it) }.toTypedArray())
        val existingFilesWithName = mapOf(*dir.getFiles().map{ Pair(it.name, it) }.toTypedArray())
        // add new files
        val newDiscoveredFiles = discoveredFilesWithName.keys.minus(existingFilesWithName.keys)
        newDiscoveredFiles.forEach {
            wasChanged = true
            val fileMedia = discoveredFilesWithName[it]!!
            val newFile = fileDao.newFile(parseFile(fileMedia, dir))
            dir.addFile(newFile)
            eventHandler.onNewNodeFound(newFile)
        }
        // remove old files
        existingFilesWithName.keys.minus(discoveredFilesWithName.keys).forEach {
            wasChanged = true
            val child = existingFilesWithName[it]!!
            dir.removeFile(child)
            fileDao.delete(child)
            eventHandler.onNodeRemoved(child)
        }
        // update files (do not re-check added files as they were just parsed)
        existingFilesWithName.keys.minus(newDiscoveredFiles).forEach {
            val child = existingFilesWithName[it]!!
            updateFile(child)
        }

        if(wasChanged)
            dirDao.save(dir)
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

        val mf = MediaFile(MediaFileEntity(MediaNode.UNALLOCATED_NODE_ID, name, parent.entity.id, type,
            TagFields(), null))

        parseTags(mf)

        //TODO parse more attributes

        return mf
    }

    private fun updateFile(file: MediaFile){
        fileToVlcMedia(file.path)!!.using(false){
            val fsVersion = parseFile(it, file.parent)
            val unchangedVersion = file.createCopy()
            var wasChanged = false

            if(file.type != fsVersion.type) {
                wasChanged = true
                file.type = fsVersion.type
            }

            if(file.chapters != fsVersion.chapters){
                wasChanged = true
                file.chapters = fsVersion.chapters
            }
            if(file.mediaTags != fsVersion.mediaTags){
                wasChanged = true
                file.mediaTags = fsVersion.mediaTags
            }
            if(file.userTags != fsVersion.userTags){
                wasChanged = true
                file.userTags = fsVersion.userTags
            }
            //TODO update more attributes

            if(wasChanged){
                RoomDB.DB_INSTANCE.mediaFileDao().save(file)
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
                Log.d(LOG_TAG, "exception in parser", e)
            }
        }
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
        protected open fun handleScanException(e: Exception){}

    }
    //endregion
}
