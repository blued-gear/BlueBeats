package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import apps.chocolatecakecodes.bluebeats.util.MediaDBEventRelay
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.ILibVLCFactory

object VlcManagers {

    private var vlc: ILibVLC? = null
    private var mediaDB: MediaDBEventRelay? = null

    fun getLibVlc(): ILibVLC{
        if(vlc === null)
            throw IllegalStateException("not initialized yet")
        return vlc!!
    }

    fun getMediaDB(): MediaDBEventRelay{
        if(mediaDB === null)
            throw IllegalStateException("not initialized yet")
        return mediaDB!!
    }

    fun init(ctx: Context){
        synchronized(this) {
            if (isInitialized())
                throw IllegalStateException("already initialized")

            val libVlcFactory = FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
            vlc = libVlcFactory.getFromContext(ctx)

            val mediaDB = MediaDBEventRelay()
            val mediaMng = MediaDB(getLibVlc(), mediaDB)
            mediaDB.setSubject(mediaMng)
            this.mediaDB = mediaDB
        }
    }

    fun isInitialized(): Boolean{
        return vlc !== null
    }
}