package org.agoranomic.assessor.dsl

import org.agoranomic.assessor.lib.getOrFail

/**
 * Represents a value that must be set exactly once.
 */
class DslValue<T> {
    private var currentValue: T? = null
    private var isInitialized: Boolean = false

    /**
     * Sets the value. Fails if the value has already been set.
     */
    fun set(value: T) {
        check(!isInitialized)
        currentValue = value
        isInitialized = true
    }

    /**
     * Gets the value. Fails if the value has not been set.
     */
    fun get(): T {
        check(isInitialized)
        return currentValue as T
    }

    /**
     * Gets the value, or a default if the value has not been set. Does not fail.
     */
    fun getOrElse(default: T): T {
        return if (isInitialized) currentValue as T else default
    }
}

/**
 * A map that contains values that may only be set exactly once per key.
 */
class DslValueMap<K, V> {
    private val map = mutableMapOf<K, DslValue<V>>()

    /**
     * Sets the value for [key]. Fails if the value has already been set for [key].
     */
    operator fun set(key: K, value: V) {
        val dslValue = map.computeIfAbsent(key) { DslValue() }
        dslValue.set(value)
    }

    /**
     * Gets the value for [key]. Fails if the value has not already been set for [key].
     */
    operator fun get(key: K): V {
        val dslValue = map.getOrFail(key)
        return dslValue.get()
    }

    /**
     * Returns whether or not a value has been set for [key].
     */
    fun containsKey(key: K): Boolean = map.containsKey(key)

    /**
     * Gets the value for [key], or [default] if it has not been set. Does not fail.
     */
    fun getOrElse(key: K, default: V): V {
        val dslValue = map[key] ?: return default
        return dslValue.getOrElse(default)
    }

    /**
     * Gets the value for [key], or null if it has not been set. Does not fail
     */
    fun getOrNull(key: K): V? {
        return map[key]?.get()
    }

    /**
     * Compiles all set keys and values into a [Map].]
     */
    fun compile(): Map<K, V> {
        return map.mapValues { (_, v) -> v.get() }
    }
}
