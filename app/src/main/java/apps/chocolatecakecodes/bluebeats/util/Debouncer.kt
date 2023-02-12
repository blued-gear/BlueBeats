package apps.chocolatecakecodes.bluebeats.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
internal class Debouncer<T> private constructor(){

    companion object {
        fun <T> create(timeout: Long, onValue: suspend (T) -> Unit): Debouncer<T> {
            val debouncer = Debouncer<T>()

            CoroutineScope(Dispatchers.Default).launch {
                callbackFlow {
                    debouncer.channel = this

                    awaitClose {  }
                }.debounce(timeout).collect {
                    onValue(it)
                }
            }

            return debouncer
        }
    }

    private lateinit var channel: SendChannel<T>

    fun debounce(value: T) {
        channel.trySend(value)
    }

    fun stop() {
        channel.close()
    }
}
