package org.randomcat.agorabot.config

import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.util.doUpdateAndExtract
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.*
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface StorageStrategy<T> {
    fun defaultValue(): T
    fun encodeToString(value: T): String
    fun decodeFromString(text: String): T
}

class AtomicCachedStorage<T>(
    private val storagePath: Path,
    private val strategy: StorageStrategy<T>,
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

    fun schedulePersistenceOn(persistenceService: ConfigPersistService) {
        persistenceService.schedulePersistence({ valueReference.get() }, { writeToFile(storagePath, strategy, it) })
    }

    fun getValue(): T {
        return valueReference.get()
    }

    fun updateValue(mapper: (T) -> T) {
        valueReference.updateAndGet(mapper)
    }
}

internal inline fun <T, V> AtomicCachedStorage<T>.updateValueAndExtract(crossinline mapper: (T) -> Pair<T, V>): V {
    return doUpdateAndExtract(this::updateValue, mapper)
}

class SchedulableAtomicCachedStorage<T>(
    storagePath: Path,
    strategy: StorageStrategy<T>,
) {
    private val impl = AtomicCachedStorage<T>(storagePath = storagePath, strategy = strategy)
    private val updateIsOngoingFlag = AtomicBoolean(false)
    private val scheduledUpdateExecutor = Executors.newSingleThreadScheduledExecutor()

    fun schedulePersistenceOn(persistService: ConfigPersistService) = impl.schedulePersistenceOn(persistService)

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
        // Only one update can run at a time because the executor is single-threaded
        updateIsOngoingFlag.set(true)

        try {
            impl.updateValue(mapper)
        } finally {
            updateIsOngoingFlag.set(false)
        }
    }

    fun updateValue(mapper: (T) -> T) {
        waitForScheduledUpdate()
        return impl.updateValue(mapper)
    }

    fun schedulePeriodicUpdate(duration: Duration, mapper: (T) -> T) {
        val delayInSeconds = duration.toSeconds()

        scheduledUpdateExecutor.scheduleAtFixedRate(
            {
                runPeriodicUpdate(mapper)
            },
            delayInSeconds,
            delayInSeconds,
            TimeUnit.SECONDS,
        )
    }
}

internal inline fun <T, V> SchedulableAtomicCachedStorage<T>.updateValueAndExtract(crossinline mapper: (T) -> Pair<T, V>): V {
    return doUpdateAndExtract(this::updateValue, mapper)
}
