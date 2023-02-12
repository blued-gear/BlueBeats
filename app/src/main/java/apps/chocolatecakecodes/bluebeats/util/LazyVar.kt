package apps.chocolatecakecodes.bluebeats.util

import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST", "ClassName")
class LazyVar<in T, V>(initializer: () -> V) : ReadWriteProperty<T, V> {

    private object UNINITIALIZED_VALUE

    private var initializer: (() -> V)? = initializer
    private var value = AtomicReference<Any>(UNINITIALIZED_VALUE)

    override fun getValue(thisRef: T, property: KProperty<*>): V {
        if(value.get() === UNINITIALIZED_VALUE){
            synchronized(this) {
                if(value.get() === UNINITIALIZED_VALUE){
                    value.set(initializer!!())
                    initializer = null// free it in case it holds other references
                }
            }
        }

        return value.get() as V
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
        if(this.value.get() !== UNINITIALIZED_VALUE) {
            this.value.set(value)
        } else {
            synchronized(this) {
                this.value.set(value)
                initializer = null// free it in case it holds other references
            }
        }
    }
}
