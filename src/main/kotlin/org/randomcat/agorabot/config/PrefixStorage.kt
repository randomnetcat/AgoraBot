package org.randomcat.agorabot.config

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.util.withTempFile
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

private val PREFIX_FILE_CHARSET = Charsets.UTF_8

class JsonPrefixMap(
    private val default: String,
    private val storagePath: Path,
) : MutableGuildPrefixMap {
    companion object {
        fun readFromFile(path: Path): Map<String, List<String>> {
            if (Files.notExists(path)) return emptyMap()

            val content = Files.readString(path, PREFIX_FILE_CHARSET)
            return Json.decodeFromString<Map<String, List<String>>>(content)
        }

        fun writeToFile(path: Path, data: Map<String, List<String>>) {
            withTempFile { tempFile ->
                val content = Json.encodeToString<Map<String, List<String>>>(data)

                Files.writeString(
                    tempFile,
                    content,
                    PREFIX_FILE_CHARSET,
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

private val logger = LoggerFactory.getLogger("AgoraBotPrefixStorage")

enum class PrefixStorageVersion {
    JSON_SINGLE_PREFIX,
    JSON_MANY_PREFIX,
}

private fun migrateSinglePrefixToManyPrefix(storagePath: Path) {
    val oldValue = Json.decodeFromString<Map<String, String>>(Files.readString(storagePath, PREFIX_FILE_CHARSET))
    val newValue = Json.encodeToString<Map<String, List<String>>>(oldValue.mapValues { (_, v) -> listOf(v) })

    Files.writeString(storagePath, newValue, PREFIX_FILE_CHARSET)
}

tailrec fun migratePrefixStorage(
    storagePath: Path,
    oldVersion: PrefixStorageVersion,
    newVersion: PrefixStorageVersion,
) {
    require(oldVersion.ordinal <= newVersion.ordinal)

    if (oldVersion == newVersion) return
    if (!Files.exists(storagePath)) return

    check(oldVersion.ordinal < newVersion.ordinal) // This should be guaranteed at this point

    logger.info("Migrating prefix storage from $oldVersion to $newVersion.")

    @Suppress("UNUSED_VARIABLE")
    val ensureUsed = when (oldVersion) {
        PrefixStorageVersion.JSON_SINGLE_PREFIX -> {
            migrateSinglePrefixToManyPrefix(storagePath)
        }

        PrefixStorageVersion.JSON_MANY_PREFIX -> check(false) {
            "This should be unreachable, as it is the newest version"
        }
    }

    return migratePrefixStorage(
        storagePath = storagePath,
        // This is guaranteed to exist because oldVersion.ordinal < newVersion.ordinal
        oldVersion = PrefixStorageVersion.values()[oldVersion.ordinal + 1],
        newVersion = newVersion,
    )
}
