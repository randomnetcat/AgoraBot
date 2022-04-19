package org.randomcat.agorabot.config.persist.impl

import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.PersistInstanceHandle
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DefaultConfigPersistService : ConfigPersistService {
    private val logger = LoggerFactory.getLogger("DefaultConfigPersistService")

    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun <T> schedulePersistence(readState: () -> T, persist: (T) -> Unit): PersistInstanceHandle {
        val isClosed = AtomicBoolean(false)
        val lock = ReentrantLock()

        var future: ScheduledFuture<*>? = executor.scheduleAtFixedRate(object : Runnable {
            private val lastValue: AtomicReference<T> = AtomicReference(null)

            override fun run() {
                try {
                    if (isClosed.get()) return

                    lock.withLock {
                        if (isClosed.get()) return

                        val newValue = readState()
                        val testLastValue = lastValue.get()

                        // Don't persist if nothing has changed.
                        if (testLastValue == newValue) return

                        lastValue.set(newValue)
                        persist(newValue)
                    }
                } catch (e: Exception) {
                    logger.error("Error while persisting storage!", e)
                }
            }
        }, 0, 5, TimeUnit.SECONDS)

        val handle = object : PersistInstanceHandle {
            override fun stopPersistence() {
                // getAndSet returns the old value. Only one thread can set this value to true, so the remainder of the
                // code in this method will return true.
                if (isClosed.getAndSet(true)) return

                future?.let {
                    it.cancel(false)

                    // Call get to wait for final cancellation.
                    runCatching {
                        it.get()
                    }
                }

                future = null

                // Unconditionally write out final state.
                try {
                    lock.withLock {
                        persist(readState())
                    }
                } catch (e: Exception) {
                    logger.error("Error while persisting storage!", e)
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            handle.stopPersistence()
        })

        return handle
    }
}
