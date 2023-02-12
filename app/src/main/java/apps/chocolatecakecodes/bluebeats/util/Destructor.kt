package apps.chocolatecakecodes.bluebeats.util

import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.atomic.AtomicBoolean

class Destructor {
    companion object{

        private val referenceQueue: ReferenceQueue<Any> = ReferenceQueue()
        private val queueThread: Thread
        private val runQueueThread: AtomicBoolean = AtomicBoolean(true)
        private val actionMap = HashMap<Reference<Any>, Runnable>()

        init{
            queueThread = Thread {
                while (runQueueThread.get()){
                    val ref = referenceQueue.remove()
                    actionMap.remove(ref)?.run()
                }
            }
            queueThread.start()
        }

        fun registerDestructor(obj: Any, action: Runnable){
            val ref = PhantomReference(obj, referenceQueue)
            actionMap[ref] = action
        }

        fun stop(){
            runQueueThread.set(false)
        }
    }
}