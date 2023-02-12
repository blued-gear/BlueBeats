package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import android.os.Handler
import android.util.Log
import apps.chocolatecakecodes.bluebeats.media.model.*
import apps.chocolatecakecodes.bluebeats.util.Utils
import apps.chocolatecakecodes.bluebeats.util.using

import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory

import java.io.FileNotFoundException
import java.io.IOException

/**
 * searches media files, extract metadata and index them, store in DB, manage tags
 * actions a synchronous but the progress can be monitored asynchronous by ScanEventHandler
 */
class MediaDB constructor(private val libVLC: ILibVLC, private val appCtx: Context, private val eventHandler: ScanEventHandler){

    companion object {
        val NOOP_EVENT_HANDLER: ScanEventHandler = object : ScanEventHandler(null) {}

        private const val IGNORE_LIST_OPTION = ":ignore-filetypes=db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,ssa,ass,smi,utf,utf-8,rt,aqt,txt,usf,jss,cdg,psb,mpsub,mpl2,pjs,dks,stl,vtt,ttml,pdf"
    }

    private val mediaFactory: IMediaFactory
    private val scanRoots: MutableSet<String>
    private var mediaTree: MediaDir = MediaNode.UNSPECIFIED_DIR
    //TODO collections that maps files to types, tags, ...

    constructor(libVLC: ILibVLC, appCtx: Context): this(libVLC, appCtx, NOOP_EVENT_HANDLER){}

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
        //TODO
        mediaTree = MediaDir("/", null)// DUMMY
    }

    fun saveDB(){
        //TODO
    }

    fun scanInAll() {
        eventHandler.onScanStarted()

        val root = MediaDir("/", null)

        for(scanRoot in scanRoots){
            val scanRootVlcDir = fileToVlcMedia(scanRoot)

            if(scanRootVlcDir == null){
                eventHandler.onScanException(IOException("unable to create vlc-media (path: $scanRoot"))
                continue
            }

            scanRootVlcDir.using(false) {
                try {
                    val scanRootNode = MediaDir(scanRoot, root)
                    val oldVersion: MediaDir? = mediaTree.findChild(scanRoot) as? MediaDir?
                    root.addDir(scanRootNode)
                    updateDirDeep(scanRootNode, oldVersion)
                }catch (e: Exception){
                    eventHandler.onScanException(e)
                }
            }
        }

        // check for deleted roots
        root.getDirs().map { it.name }.toHashSet().let { dirNames ->
            mediaTree.getDirs().filter { !dirNames.contains(it.name) }.forEach {
                eventHandler.onNodeRemoved(it)
            }
        }

        mediaTree = root
        eventHandler.onScanFinished()
    }

    fun getMediaTreeRoot(): MediaDir{
        return mediaTree
    }

    fun fileToMedia(path: String): MediaFile{
        val vlcMedia = fileToVlcMedia(path)
        if(vlcMedia === null)
            throw FileNotFoundException("file $path can not be converted to vlc-media")
        val ret = MediaFile(vlcMedia, MediaNode.UNSPECIFIED_DIR)
        vlcMedia.release()
        return ret
    }
    fun fileToVlcMedia(path: String): IMedia?{
        return mediaFactory.getFromLocalPath(libVLC, path);
    }
    //endregion

    //region private methods

    /**
     * @param dir parsed dir which should be scanned
     * @param target a freshly initialized dir where the discovered contest will be put
     */
    private fun scanDir(dir: IMedia, target: MediaDir){
        if(!Utils.vlcMediaToDocumentFile(dir).isDirectory)
            throw IllegalArgumentException("path is not a directory")
        if(target.getDirs().isNotEmpty() or target.getFiles().isNotEmpty())
            throw java.lang.IllegalArgumentException("target must be a freshly initialized MediaDir")

        dir.using {
            dir.addOption(IGNORE_LIST_OPTION)
            dir.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

            for(i in 0 until dir.subItems().count){
                val child = dir.subItems().getMediaAt(i)
                child.addOption(IGNORE_LIST_OPTION)
                child.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

                when (child.type) {
                    IMedia.Type.Directory -> {
                        assert(Utils.vlcMediaToDocumentFile(child).isDirectory)
                        target.addDir(MediaDir(child.uri.lastPathSegment!!, target))
                    }
                    IMedia.Type.File -> {
                        assert(Utils.vlcMediaToDocumentFile(child).isFile)
                        target.addFile(parseFile(child, target))
                    }
                    else -> {
                        Log.d("MediaDB", "unknown media type encountered: ${child.type}  -  ${child.uri}")
                    }
                }
            }
        }
    }

    private fun updateDirDeep(dir: MediaDir, oldVersion: MediaDir?){
        val vlcDir = fileToVlcMedia(dir.path) ?: throw IOException("unable to create media")
        vlcDir.using(false) {
            // scan top level
            scanDir(vlcDir, dir)

            // diff top level
            diffDir(dir, oldVersion)

            // process sub-dirs
            for(subDir in dir.getDirs()){
                val oldSubDir: MediaDir? = oldVersion?.findChild(subDir.name) as? MediaDir?
                updateDirDeep(subDir, oldSubDir)
            }
        }
    }

    private fun diffDir(dir: MediaDir, oldDir: MediaDir?){//TODO should a onNodeUpdated be fired if the dir-content changes (new / removed files / dirs)?
        // check if dir is new
        if(oldDir === null){
            eventHandler.onNewNodeFound(dir)
            return
        }

        // check for deleted dirs and files
        dir.getDirs().map { it.name }.toHashSet().let { dirNames ->
            oldDir.getDirs().filter { !dirNames.contains(it.name) }.forEach {
                eventHandler.onNodeRemoved(it)
            }
        }
        dir.getFiles().map { it.name }.toHashSet().let { fileNames ->
            oldDir.getFiles().filter { !fileNames.contains(it.name) }.forEach {
                eventHandler.onNodeRemoved(it)
            }
        }

        // check for new or changed files
        val oldFiles: Map<String, MediaFile> = mapOf(*oldDir.getFiles().map { Pair(it.name, it) }.toTypedArray())
        dir.getFiles().forEach {
            val oldFile = oldFiles[it.name]
            if(oldFile === null){
                eventHandler.onNewNodeFound(it)
            }else{
                if(it != oldFile)
                    eventHandler.onNodeUpdated(it, oldFile)
            }
        }
    }

    private fun parseFile(file: IMedia, parent: MediaDir): MediaFile{
        val mediaFile = MediaFile(file, parent)

        //TODO extract extra attrs (chapters, tags, ...)

        return mediaFile
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