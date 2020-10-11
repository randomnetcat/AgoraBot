package org.randomcat.agorabot.config

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface ConfigPersistService {
    /**
     * Schedules the periodic persistence of the return value of [readState] via [persist]. [persist] should be able to
     * handle being called concurrently. The scheduling is on a best-effort basis, and may only result in a relatively
     * recent value being persisted, rather than the One True Up-To-Date Value.
     *
     * Note that [persist] may be called during JVM shutdown, and thus it should avoid locking things if at all
     * possible.
     */
    fun <T> schedulePersistence(readState: () -> T, persist: (T) -> Unit)
}

object DefaultConfigPersistService : ConfigPersistService {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    // A flag that is set during JVM shutdown so that normal writing does not start during JVM shutdown.
    private val shutdownFlag = AtomicBoolean(false)

    override fun <T> schedulePersistence(readState: () -> T, persist: (T) -> Unit) {
        val lock = ReentrantLock()

        executor.scheduleAtFixedRate(object : Runnable {
            private val lastValue: AtomicReference<T> = AtomicReference(null)

            override fun run() {
                // Don't try to go through with normal writing during shutdown. The shutdown hook (added below) will
                // handle the final write.
                if (shutdownFlag.get()) return

                lock.withLock {
                    val newValue = readState()
                    val testLastValue = lastValue.get()

                    // Don't persist if nothing has changed. This doesn't need to be some super complicated atomic
                    // operation thing because persist must be thread-safe and if we skip any changes, the next time can
                    // just pick up the slack.
                    if (testLastValue == newValue) return

                    lastValue.set(newValue)
                    persist(newValue)
                }
            }
        }, 0, 5, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread {
            // Mark that the JVM is shutting down. Do this here instead of in a single shutdown hook in order to
            // increase the chance that it happens earlier, since shutdown hooks are run in an arbitrary order.
            shutdownFlag.set(true)

            // Don't block on the lock during JVM shutdown. If we can't acquire it, then another thread is already
            // writing the config, and everything is fine.
            if (lock.tryLock()) {
                try {
                    persist(readState())
                } finally {
                    lock.unlock()
                }
            }
        })
    }
}
