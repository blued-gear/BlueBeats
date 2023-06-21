package apps.chocolatecakecodes.bluebeats.util

import java.util.Observable
import java.util.Observer

class SimpleObservable<T>(initialValue: T) : Observable() {

    private var value: T = initialValue

    fun get(): T {
        return value
    }

    fun set(newValue: T) {
        value = newValue

        this.setChanged()
        this.notifyObservers(newValue)
    }

    fun addObserverCallback(callback: (Observable, T) -> Unit): Observer {
        return Observer { o, arg ->
            callback(o, arg as T)
        }.also {
            this.addObserver(it)
        }
    }
}
