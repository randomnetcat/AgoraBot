package org.randomcat.agorabot.config.persist

import kotlinx.collections.immutable.persistentListOf
import org.randomcat.agorabot.util.doUpdateAndExtract
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.*
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

interface StorageStrategy<T> {
    fun defaultValue(): T
    fun encodeToString(value: T): String
    fun decodeFromString(text: String): T
}

class AtomicCachedStorage<T>(
    private val storagePath: Path,
    private val strategy: StorageStrategy<T>,
    persistService: ConfigPersistService,
) {
    companion object {
        private fun <T> writeToFile(storagePath: Path, strategy: StorageStrategy<T>, value: T) {
            withTempFile { tempFile ->
                val content = strategy.encodeToString(value)

                Files.writeString(
                    tempFile,
                    content,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )

                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        private fun <T> readFromFile(storagePath: Path, strategy: StorageStrategy<T>): T {
            return strategy.decodeFromString(
                try {
                    Files.readString(storagePath)
                } catch (e: NoSuchFileException) {
                    return strategy.defaultValue()
                }
            )
        }
    }

    private val valueReference: AtomicReference<T> = AtomicReference<T>(readFromFile(storagePath, strategy))

    private val persistHandle =
        persistService.schedulePersistence({ valueReference.get() }, { writeToFile(storagePath, strategy, it) })

    private var isClosed = false
    private val closedLock = ReentrantReadWriteLock()

    fun getValue(): T {
        closedLock.read {
            check(!isClosed)
            return valueReference.get()
        }
    }

    fun updateValue(mapper: (T) -> T) {
        closedLock.read {
            check(!isClosed)
            valueReference.updateAndGet(mapper)
        }
    }

    fun close() {
        closedLock.write {
            if (isClosed) return

            isClosed = true
            persistHandle.stopPersistence()
        }
    }
}

inline fun <T, V> AtomicCachedStorage<T>.updateValueAndExtract(crossinline mapper: (T) -> Pair<T, V>): V {
    return doUpdateAndExtract(this::updateValue, mapper)
}

class SchedulableAtomicCachedStorage<T>(
    storagePath: Path,
    strategy: StorageStrategy<T>,
    persistService: ConfigPersistService,
) {
    private val impl =
        AtomicCachedStorage<T>(storagePath = storagePath, strategy = strategy, persistService = persistService)
    private val updateIsOngoingFlag = AtomicBoolean(false)
    private val scheduledUpdateExecutor = Executors.newSingleThreadScheduledExecutor()

    private val closedLock = ReentrantReadWriteLock()
    private var isClosed = false

    private val scheduledFuturesLock = ReentrantLock()
    private val scheduledFutures = persistentListOf<ScheduledFuture<*>>()

    fun getValue() = impl.getValue()

    /**
     * Use to minimize the contention between scheduled updates and ad-hoc updates. Scheduled updates might take a while
     * to do computation, so this ensures that the update thread isn't spinning forever.
     */
    private fun waitForScheduledUpdate() {
        while (updateIsOngoingFlag.get()) {
            Thread.onSpinWait()
        }
    }

    private fun runPeriodicUpdate(mapper: (T) -> T) {
        closedLock.read {
            if (isClosed) return

            // Only one update can run at a time because the executor is single-threaded
            updateIsOngoingFlag.set(true)

            try {
                impl.updateValue(mapper)
            } finally {
                updateIsOngoingFlag.set(false)
            }
        }
    }

    fun updateValue(mapper: (T) -> T) {
        closedLock.read {
            check(!isClosed)

            waitForScheduledUpdate()
            return impl.updateValue(mapper)
        }
    }

    fun schedulePeriodicUpdate(duration: Duration, mapper: (T) -> T) {
        closedLock.read {
            check(!isClosed)

            val delayInSeconds = duration.toSeconds()

            val future = scheduledUpdateExecutor.scheduleAtFixedRate(
                {
                    runPeriodicUpdate(mapper)
                },
                delayInSeconds,
                delayInSeconds,
                TimeUnit.SECONDS,
            )

            scheduledFuturesLock.withLock {
                scheduledFutures.add(future)
            }
        }
    }

    fun close() {
        closedLock.write {
            if (isClosed) return
            isClosed = true
        }

        scheduledFuturesLock.withLock {
            scheduledFutures.forEach {
                it.cancel(false)

                // Call get to wait for final cancellation.
                runCatching { it.get() }
            }
        }

        impl.close()
    }
}

inline fun <T, V> SchedulableAtomicCachedStorage<T>.updateValueAndExtract(crossinline mapper: (T) -> Pair<T, V>): V {
    return doUpdateAndExtract(this::updateValue, mapper)
}
