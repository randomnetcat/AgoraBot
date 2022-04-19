package org.randomcat.agorabot.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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

    private val closeLock = ReentrantReadWriteLock()

    // Only accessed under closeLock.
    private var isClosed: Boolean = false

    private val data: AtomicReference<PersistentMap<K, LoadOnceValue<V>>> = AtomicReference(persistentMapOf())

    fun getOrPut(key: K, initOnce: () -> V): V {
        closeLock.read {
            check(!isClosed)

            run {
                val origMap = data.get()

                // getOrElse is not atomic, but origMap is immutable, so that's fine.
                // If the key is not in the map, getOrElse will return from the run block, so the below updateAndGet
                // call will run. If it is in the map, getOrElse will return the LoadOnceValue, and the lazy value will
                // be read, and the standard library will handle thread-safely invoking the callable exactly once.
                return origMap.getOrElse(key) {
                    return@run
                }.value.getOrThrow()
            }

            return data.updateAndGet {
                if (it.containsKey(key))
                    it
                else
                // If multiple threads get here, they will race to return a new map with a value. Because this is an atomic
                // operation, then only one of them will succeed (returning and initializing the value), and the rest will
                // get to try again. They will find that it already contains the key, and return the existing map, ensuring
                // that only one LoadOnceValue escapes to the outside world. That LoadOnceValue will then thread-safely
                // initialize the value once, ensuring that initOnce is called exactly once for each key (assuming it never
                // throws an exception).
                    it.put(key, LoadOnceValue<V>(initOnce))
            }.getValue(key).value.getOrThrow()
        }
    }

    /**
     * Closes this AtomicLoadOnceMap (meaning that all future calls to getOrPut will throw) and retrieves the internal
     * map of keys to values. This method can only be called once; any further calls will throw.
     *
     * Any entries produced by functions that threw during evaluation are not included.
     */
    fun closeAndTake(): Map<K, V> {
        closeLock.write {
            check(!isClosed)
            isClosed = true

            // All accesses to the value property occur under the read lock, so this should not run code under the
            // lock.
            return data.get().filterValues {
                // Take only values where accessing value itself does not throw and where the init function actually
                // produced a value.
                runCatching { it.value.isSuccess }.getOrDefault(false)
            }.mapValues { (_, v) -> v.value.getOrThrow() }
        }
    }
}
