package apps.chocolatecakecodes.bluebeats.util

/**
 * Used to indicate that a property can be set to null
 * but will not be null when the parent object is used properly
 */
class RequireNotNull<V> constructor(){

    private var value: V? = null

    constructor(initialValue: V?) : this() {
        set(initialValue)
    }

    fun get(): V {
        return value!!
    }

    fun getNullable(): V? {
        return value
    }

    fun set(value: V?) {
        this.value = value
    }

    fun isNull() : Boolean {
        return value === null
    }
}
