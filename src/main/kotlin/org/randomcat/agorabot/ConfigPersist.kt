package org.randomcat.agorabot

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    override fun <T> schedulePersistence(readState: () -> T, persist: (T) -> Unit) {
        executor.scheduleAtFixedRate(object : Runnable {
            private val lastValue: AtomicReference<T> = AtomicReference(null)

            override fun run() {
                val newValue = readState()
                val testLastValue = lastValue.get()

                // Don't persist if nothing has changed. This doesn't need to be some super complicated atomic operation
                // thing because persist must be thread-safe and if we skip any changes, the next time can just pick
                // up the slack.
                if (testLastValue == newValue) return

                lastValue.set(newValue)
                persist(newValue)
            }
        }, 0, 5, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread {
            persist(readState())
        })
    }
}
