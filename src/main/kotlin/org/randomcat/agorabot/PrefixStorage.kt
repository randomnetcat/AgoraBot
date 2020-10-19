package org.randomcat.agorabot

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

class JsonPrefixMap(
    private val default: String,
    storagePath: Path,
    persistenceService: ConfigPersistService,
) : MutableGuildPrefixMap {
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

    private val map: AtomicReference<PersistentMap<String, String>> =
        AtomicReference(readFromFile(storagePath).toPersistentMap())

    init {
        persistenceService.schedulePersistence({ map.get() }, { writeToFile(storagePath, it) })
    }

    override fun setPrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap -> oldMap.put(guildId, prefix) }
    }

    override fun prefixForGuild(guildId: String): String {
        return map.get().getOrDefault(guildId, default)
    }
}
