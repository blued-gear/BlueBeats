package apps.chocolatecakecodes.bluebeats.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Used to indicate that a property is only once settable, even if it is 'var'
 */
class OnceSettable<in T, V> : ReadWriteProperty<T, V> {

    private var value: ValueWrapper<V>? = null

    override fun getValue(thisRef: T, property: KProperty<*>): V {
        val wrapper = value
        if(wrapper === null)
            throw UninitializedPropertyAccessException("no value set yet")

        return wrapper.value
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
        if(this.value !== null)
            throw IllegalStateException("value already set")

        this.value = ValueWrapper(value)
    }

    private class ValueWrapper<T>(val value: T){}
}