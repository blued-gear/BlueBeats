package apps.chocolatecakecodes.bluebeats.util

import apps.chocolatecakecodes.bluebeats.media.MediaDB
import apps.chocolatecakecodes.bluebeats.media.model.MediaNode
import java.util.concurrent.atomic.AtomicReference

internal class MediaDBEventRelay : MediaDB.ScanEventHandler(null) {

    private val subscribers = mutableListOf<MediaDB.ScanEventHandler>()
    private var subject: AtomicReference<MediaDB> = AtomicReference(null)

    //region public methods

    fun setSubject(mediaDB: MediaDB){
        if(!subject.compareAndSet(null, mediaDB))
            throw IllegalStateException("subject already set")
    }
    fun getSubject(): MediaDB{
        val subject = this.subject.get()
        if(subject === null)
            throw IllegalStateException("subject not set")
        return subject
    }

    fun addSubscriber(handler: MediaDB.ScanEventHandler){
        synchronized(this) {
            subscribers.add(handler)
        }
    }
    fun removeSubscriber(handler: MediaDB.ScanEventHandler){
        synchronized(this) {
            subscribers.remove(handler)
        }
    }
    //endregion

    // region relay methods

    override fun handleScanStarted() {
        synchronized(this){
            subscribers.forEach {
                it.onScanStarted()
            }
        }
    }
    override fun handleScanFinished() {
        synchronized(this){
            subscribers.forEach {
                it.onScanFinished()
            }
        }
    }
    override fun handleNewNodeFound(node: MediaNode) {
        synchronized(this){
            subscribers.forEach {
                it.onNewNodeFound(node)
            }
        }
    }
    override fun handleNodeRemoved(node: MediaNode) {
        synchronized(this){
            subscribers.forEach {
                it.onNodeRemoved(node)
            }
        }
    }
    override fun handleNodeUpdated(node: MediaNode, oldVersion: MediaNode) {
        synchronized(this){
            subscribers.forEach {
                it.onNodeUpdated(node, oldVersion)
            }
        }
    }
    override fun handleScanException(e: Exception) {
        synchronized(this){
            subscribers.forEach {
                it.onScanException(e)
            }
        }
    }
    //endregion
}