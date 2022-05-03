package org.randomcat.agorabot.versioning_storage.impl

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.util.withTempFile
import org.randomcat.agorabot.versioning_storage.api.VersioningStorage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JsonVersioningStorage(private val storagePath: Path) : VersioningStorage {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        fun readFromFile(path: Path): Map<String, String> {
            if (Files.notExists(path)) return emptyMap()

            val content = Files.readString(path, FILE_CHARSET)
            return Json.decodeFromString<Map<String, String>>(content)
        }

        fun writeToFile(path: Path, data: Map<String, String>) {
            withTempFile { tempFile ->
                val content = Json.encodeToString<Map<String, String>>(data)

                Files.writeString(
                    tempFile,
                    content,
                    FILE_CHARSET,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )

                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private val lock: Lock = ReentrantLock()
    private var cache: PersistentMap<String, String> = readFromFile(storagePath).toPersistentMap()

    override fun versionFor(component: String): String? {
        return lock.withLock { cache[component] }
    }

    override fun setVersion(component: String, version: String) {
        lock.withLock {
            if (versionFor(component) != version) {
                val updatedValue = cache.put(component, version)

                writeToFile(storagePath, updatedValue)
                cache = updatedValue
            }
        }
    }
}
