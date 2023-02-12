package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import apps.chocolatecakecodes.bluebeats.media.model.*
import apps.chocolatecakecodes.bluebeats.util.Utils
import com.anggrayudi.storage.file.getAbsolutePath

import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.MediaBrowser

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * searches media files, extract metadata and index them, store in DB, manage tags
 * actions a synchronous but the progress can be monitored asynchronous by ScanEventHandler
 */
class MediaDB constructor(private val libVLC: ILibVLC, private val appCtx: Context, private val eventHandler: ScanEventHandler){

    companion object {
        val NOOP_EVENT_HANDLER: ScanEventHandler = object : ScanEventHandler(null) {}

        private const val IGNORE_LIST_OPTION = ":ignore-filetypes=db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv,txt,sub,idx,srt,ssa,ass,smi,utf,utf-8,rt,aqt,txt,usf,jss,cdg,psb,mpsub,mpl2,pjs,dks,stl,vtt,ttml"
    }

    private val mediaFactory: IMediaFactory
    private var mediaTree: MediaDir = MediaNode.UNSPECIFIED_DIR
    //TODO collections that maps files to types, tags, ...

    constructor(libVLC: ILibVLC, appCtx: Context): this(libVLC, appCtx, NOOP_EVENT_HANDLER){}

    init{
        mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    }

    //region public methods

    fun loadDB(){
        //TODO
        mediaTree = MediaDir("/", null)// DUMMY
    }

    fun saveDB(){
        //TODO
    }

    fun scanInAll(root: String) {
        //TODO scan internal and media roots, update tree, save DB

        val b = MediaBrowser(libVLC, object: MediaBrowser.EventListener{
            override fun onMediaAdded(index: Int, media: IMedia?) {
                Log.d("MediaBrowser", "media found: ${media?.uri} ($index)")
            }
            override fun onMediaRemoved(index: Int, media: IMedia?) {
                Log.d("MediaBrowser", "media removed: ${media?.uri} ($index)")
            }
            override fun onBrowseEnd() {
                Log.d("MediaBrowser", "scan done")
            }

        })
        b.browse(root, MediaBrowser.Flag.Interact)

        val rootVlcDir = mediaFactory.getFromLocalPath(libVLC, root) ?: throw IOException("unable to create media")
        rootVlcDir.addOption(IGNORE_LIST_OPTION)
        rootVlcDir.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

        val rootParent: MediaDir = Utils.dirTreeToMediaDir(Utils.parentDir(root))
        val rootMediaDir = scanDir(rootVlcDir, rootParent)
        updateDirDeep(rootMediaDir, mediaTree)
        mediaTree = rootMediaDir
        rootVlcDir.release()
    }

    fun getMediaTreeRoot(): MediaDir{
        return mediaTree
    }

    fun fileToMedia(path: String): MediaFile{
        val vlcMedia = fileToVlcMedia(path)
        if(vlcMedia === null)
            throw FileNotFoundException("file $path can not be converted to vlc-media")
        return MediaFile(vlcMedia, MediaNode.UNSPECIFIED_DIR)
    }
    fun fileToVlcMedia(path: String): IMedia?{
        return mediaFactory.getFromLocalPath(libVLC, path);
    }
    //endregion

    //region private methods

    private fun updateDirDeep(dir: MediaDir, oldVersion: MediaDir?){
        //TODO cmp with oldVersion and fire onNodeUpdated event
        for(subDir in dir.getDirs()){
            val vlcDir = fileToVlcMedia(subDir.path) ?: throw IOException("unable to create media")
            vlcDir.addOption(IGNORE_LIST_OPTION)
            vlcDir.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

            val scannedDir = scanDir(vlcDir, dir)
            //TODO or should I just replace the current item
            for(f in scannedDir.getFiles())
                subDir.addFile(f)
            for(d in scannedDir.getDirs())
                subDir.addDir(d)

            updateDirDeep(subDir, null /*TODO*/)
        }
    }
    /**
     * @param dir parsed dir which should be scanned
     */
    private fun scanDir(dir: IMedia, parent: MediaDir?): MediaDir{
        if(!Utils.vlcMediaToDocumentFile(dir).isDirectory)
            throw IllegalArgumentException("path is not a directory")

        dir.retain()

        val mediaDir = MediaDir(dir.uri.lastPathSegment!!, parent)

        for(i in 0 until dir.subItems().count){
            val child = dir.subItems().getMediaAt(i)
            child.addOption(IGNORE_LIST_OPTION)
            child.parse(IMedia.Parse.ParseLocal or IMedia.Parse.DoInteract)

            //TODO cmp each element with old version
            when (child.type) {
                IMedia.Type.Directory -> {
                    assert(Utils.vlcMediaToDocumentFile(child).isDirectory)
                    mediaDir.addDir(MediaDir(child.uri.lastPathSegment!!, mediaDir))
                }
                IMedia.Type.File -> {
                    assert(Utils.vlcMediaToDocumentFile(child).isFile)
                    mediaDir.addFile(parseFile(child, mediaDir))
                }
                else -> {
                    Log.d("MediaDB", "unknown media type encountered: ${child.type}  -  ${child.uri}")
                }
            }
        }

        dir.release()
        return mediaDir
    }

    private fun parseFile(file: IMedia, parent: MediaDir): MediaFile{
        val mediaFile = MediaFile(file, parent)

        //TODO extract extra attrs (chapters, tags, ...)

        return mediaFile
    }
    //endregion

    //region classes

    abstract class ScanEventHandler(private val dispatcher: Handler?){

        internal fun onScanStarted(){
            if(dispatcher !== null){
                dispatcher.post { handleScanStarted() }
            }else{
                handleScanStarted()
            }
        }
        internal fun onScanStopped(){
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