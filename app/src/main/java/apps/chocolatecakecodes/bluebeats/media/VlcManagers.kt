package apps.chocolatecakecodes.bluebeats.media

import android.content.Context
import apps.chocolatecakecodes.bluebeats.util.MediaDBEventRelay
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.ILibVLCFactory
import java.util.concurrent.atomic.AtomicReference

internal object VlcManagers {

    private val vlc = AtomicReference<ILibVLC?>()
    private val mediaDB = AtomicReference<MediaDBEventRelay?>()

    fun getLibVlc(): ILibVLC{
        if(vlc.get() === null)
            throw IllegalStateException("not initialized yet")
        return vlc.get()!!
    }

    fun getMediaDB(): MediaDBEventRelay{
        if(mediaDB.get() === null)
            throw IllegalStateException("not initialized yet")
        return mediaDB.get()!!
    }

    fun init(ctx: Context){
        synchronized(this) {
            if (isInitialized())
                throw IllegalStateException("already initialized")

            val libVlcFactory = FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
            vlc.set(libVlcFactory.getFromContext(ctx))

            val mediaDB = MediaDBEventRelay()
            val mediaMng = MediaDB(getLibVlc(), mediaDB)
            mediaDB.setSubject(mediaMng)
            this.mediaDB.set(mediaDB)
        }
    }

    fun isInitialized(): Boolean{
        return vlc.get() !== null
    }
}