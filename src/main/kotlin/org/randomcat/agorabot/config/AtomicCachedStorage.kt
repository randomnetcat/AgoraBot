package org.randomcat.agorabot.config

import org.randomcat.agorabot.util.withTempFile
import java.nio.file.*
import java.util.concurrent.atomic.AtomicReference

class AtomicCachedStorage<T>(
    private val storagePath: Path,
    private val strategy: Strategy<T>,
) {
    companion object {
        private fun <T> writeToFile(storagePath: Path, strategy: Strategy<T>, value: T) {
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

        private fun <T> readFromFile(storagePath: Path, strategy: Strategy<T>): T {
            return strategy.decodeFromString(
                try {
                    Files.readString(storagePath)
                } catch (e: NoSuchFileException) {
                    return strategy.defaultValue()
                }
            )
        }
    }

    interface Strategy<T> {
        fun defaultValue(): T
        fun encodeToString(value: T): String
        fun decodeFromString(text: String): T
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