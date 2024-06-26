package org.randomcat.agorabot.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AtomicLoadOnceMap<K, V>(private val closer: ((V) -> Unit)?) {
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

    private val isClosing = AtomicBoolean(false)
    private val fullyClosedLatch = CountDownLatch(1)

    fun getOrPut(key: K, initOnce: () -> V): V {
        val loadOnceValue = lock.withLock {
            check(!isClosing.get())

            data.getOrElse(key) {
                val value = LoadOnceValue(initOnce)
                data = data.put(key, value)

                value
            }
        }

        return loadOnceValue.value.getOrThrow()
    }

    fun close() {
        if (isClosing.getAndSet(true)) {
            fullyClosedLatch.await()
            return
        }

        try {
            val closer = closer

            if (closer != null) {
                // If any concurrent writes calls to getOrPut happen before this, we will see their effects, which is
                // fine. Any current calls to getOrPut that happen after this must see isClosing = true and thus
                // throw before writing.
                val map = lock.withLock {
                    val out = data
                    data = persistentMapOf()

                    out
                }

                sequentiallyClose(
                    map
                        .values
                        .asSequence()
                        .map { it.value }
                        .filter { it.isSuccess }
                        .map { { closer(it.getOrThrow()) } }
                        .asIterable(),
                )
            }
        } finally {
            fullyClosedLatch.countDown()
        }
    }
}
