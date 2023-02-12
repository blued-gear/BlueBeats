package apps.chocolatecakecodes.bluebeats.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class CachedReference<out T, in P>(parent: P, private val maxIdleTime: Long, private val initializer: () -> T) : ReadOnlyProperty<P, T>{

    private val parentRef: WeakReference<P>
    private var value: ValueWrapper<T>? = null
    @Volatile
    private var lastAccess: Long

    init{
        if(parent === null)
            throw IllegalArgumentException("parent must not be null")

        parentRef = WeakReference(parent)
        lastAccess = System.currentTimeMillis()

        val task = TimerTask()
        TimerThread.INSTANCE.addInterval(task::execute, maxIdleTime)
        Destructor.registerDestructor(parent){
            task.cancel = true
        }
    }

    override fun getValue(thisRef: P, property: KProperty<*>): T {
        if(thisRef !== parentRef.get())
            throw IllegalArgumentException("delegate can not be used for more than one property")

        lastAccess = System.currentTimeMillis()

        var wrapper = value
        if(wrapper === null){
            wrapper = ValueWrapper(initializer())
            value = wrapper
        }

        return wrapper.value
    }

    private inner class TimerTask{

        @Volatile
        var cancel = false

        fun execute(): Long{
            if(cancel) return -1

            val nextInterval = maxIdleTime - (System.currentTimeMillis() - lastAccess)

            if(nextInterval <= 0){
                // time is up -> clear reference
                value = null
                return maxIdleTime
            }else{
                return nextInterval
            }
        }
    }

    private class ValueWrapper<T>(val value: T){}
}