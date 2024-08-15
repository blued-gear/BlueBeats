package apps.chocolatecakecodes.bluebeats.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Used to hold a value and enqueue operations until it is ready
 * @param readyMapper maps the holder value to the usable value or null if not ready
 */
internal class EventualValue<H, T>(
    private val actionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val readyMapper: (H) -> T?
) {

    var holder: H? = null

    private val actionQueue = ConcurrentLinkedQueue<(T) -> Unit>()
    private val queueWorker: Job

    init {
        queueWorker = CoroutineScope(Dispatchers.Default).launch {
            while(true) {
                delay(50)
                processQueue()
                if(!this.isActive) return@launch
            }
        }
    }

    suspend fun await(): T {
        holder?.let { readyMapper(it) }?.let {
            return it
        }

        val ret = CompletableDeferred<T>(queueWorker)
        actionQueue.offer {
            ret.complete(it)
        }
        return ret.await()
    }

    fun await(action: suspend (T) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            val value = await()

            withContext(actionDispatcher) {
                action(value)
            }
        }
    }

    fun destroy() {
        queueWorker.cancel()
    }

    private fun processQueue() {
        val value = holder?.let { readyMapper(it) } ?: return

        var action = actionQueue.poll()
        while(action != null) {
            action(value)
            action = actionQueue.poll()
        }
    }
}
