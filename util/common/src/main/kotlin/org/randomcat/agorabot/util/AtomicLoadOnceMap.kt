package org.randomcat.agorabot.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AtomicLoadOnceMap<K, V> {
    private class LoadOnceValue<V>(init: () -> V) {
        // Only accessed under the lock of lazy.
        private var attempted = false

        // Any exceptions thrown by the block will be caught and converted to a graceful return.
        // If runCatching itself throws, the attempted flag will still have been set to true, preventing
        // any further calls to init.
        val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            check(!attempted) { "Previous attempt failed and was not caught" }
            attempted = true
            runCatching(init)
        }
    }

    private val lock = ReentrantLock()
    private var data: PersistentMap<K, LoadOnceValue<V>> = persistentMapOf()
    private var isClosed: Boolean = false

    fun getOrPut(key: K, initOnce: () -> V): V {
        val loadOnceValue = lock.withLock {
            check(!isClosed)

            data.getOrElse(key) {
                val value = LoadOnceValue(initOnce)
                data = data.put(key, value)

                value
            }
        }

        return loadOnceValue.value.getOrThrow()
    }

    /**
     * Closes this AtomicLoadOnceMap (meaning that all future calls to getOrPut will throw) and retrieves the internal
     * map of keys to values. This method can only be called once; any further calls will throw.
     *
     * Any entries produced by functions that threw during evaluation are not included.
     */
    fun closeAndTake(): Map<K, V> {
        val map = lock.withLock {
            check(!isClosed)
            isClosed = true

            val out = data
            data = persistentMapOf()

            out
        }

        return map.filterValues {
            // Take only values where accessing value itself does not throw and where the init function actually
            // produced a value.
            runCatching { it.value.isSuccess }.getOrDefault(false)
        }.mapValues { (_, v) -> v.value.getOrThrow() }
    }
}
