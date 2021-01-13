package org.randomcat.agorabot.config

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

class JsonPrefixMap(
    private val default: String,
    private val storagePath: Path,
) : MutableGuildPrefixMap {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        fun readFromFile(path: Path): Map<String, List<String>> {
            if (Files.notExists(path)) return emptyMap()

            val content = Files.readString(path, FILE_CHARSET)
            return Json.decodeFromString<Map<String, List<String>>>(content)
        }

        fun writeToFile(path: Path, data: Map<String, List<String>>) {
            withTempFile { tempFile ->
                val content = Json.encodeToString<Map<String, List<String>>>(data)

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

    private val map: AtomicReference<PersistentMap<String, List<String>>> =
        AtomicReference(readFromFile(storagePath).toPersistentMap())

    fun schedulePersistenceOn(persistenceService: ConfigPersistService) {
        persistenceService.schedulePersistence({ map.get() }, { writeToFile(storagePath, it) })
    }

    override fun addPrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap ->
            val old = oldMap.getOrDefault(guildId, listOf(default))
            // Put the longest prefixes first, so if we have overlapping prefixes--say "please"
            // and "please please", both "please cfj" and "please please cfj" work as expected
            oldMap.put(guildId, (old + listOf(prefix)).sortedByDescending { it.length })
        }
    }

    override fun removePrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap ->
            val old = oldMap.getOrDefault(guildId, listOf(default))
            oldMap.put(guildId, old.filter { it != prefix })
        }
    }

    override fun prefixesForGuild(guildId: String): List<String> {
        return map.get().getOrDefault(guildId, listOf(default))
    }
}
