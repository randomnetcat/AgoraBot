package org.randomcat.agorabot.config

import kotlinx.collections.immutable.*
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
import java.nio.file.NoSuchFileException as NioNoSuchFileException

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

    private val map: AtomicReference<PersistentMap<String, PersistentList<String>>> = AtomicReference(
        readFromFile(storagePath).mapValues { (_, v) -> v.toPersistentList() }.toPersistentMap()
    )

    fun schedulePersistenceOn(persistenceService: ConfigPersistService) {
        persistenceService.schedulePersistence({ map.get() }, { writeToFile(storagePath, it) })
    }

    override fun addPrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap ->
            val old = oldMap.getOrDefault(guildId, persistentListOf(default))
            // Put the longest prefixes first, so if we have overlapping prefixes--say "please"
            // and "please please", both "please cfj" and "please please cfj" work as expected
            oldMap.put(guildId, old.add(prefix).sortedByDescending { it.length }.toPersistentList())
        }
    }

    override fun removePrefixForGuild(guildId: String, prefix: String) {
        map.updateAndGet { oldMap ->
            val old = oldMap.getOrDefault(guildId, persistentListOf(default))
            oldMap.put(guildId, old.removeAll { it == prefix })
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

private fun convertSinglePrefixToManyPrefix(prefixDataText: String): String {
    val oldValue = Json.decodeFromString<Map<String, String>>(prefixDataText)
    return Json.encodeToString<Map<String, List<String>>>(oldValue.mapValues { (_, v) -> listOf(v) })
}

private tailrec fun convertPrefixData(
    prefixDataText: String,
    oldVersion: PrefixStorageVersion,
    newVersion: PrefixStorageVersion,
): String {
    require(oldVersion.ordinal <= newVersion.ordinal)

    if (oldVersion == newVersion) return prefixDataText

    check(oldVersion.ordinal < newVersion.ordinal) // This should be guaranteed at this point

    val oneStepConverted = when (oldVersion) {
        PrefixStorageVersion.JSON_SINGLE_PREFIX -> {
            convertSinglePrefixToManyPrefix(prefixDataText)
        }

        PrefixStorageVersion.JSON_MANY_PREFIX -> {
            error("This should be unreachable, as it is the newest version")
        }
    }

    return convertPrefixData(
        prefixDataText = oneStepConverted,
        // This is guaranteed to exist because oldVersion.ordinal < newVersion.ordinal
        oldVersion = PrefixStorageVersion.values()[oldVersion.ordinal + 1],
        newVersion = newVersion,
    )
}

fun migratePrefixStorage(
    storagePath: Path,
    oldVersion: PrefixStorageVersion,
    newVersion: PrefixStorageVersion,
) {
    if (oldVersion == newVersion) return // Fast path to avoid opening the file when unnecessary

    val prefixDataText = try {
        Files.readString(storagePath, PREFIX_FILE_CHARSET)
    } catch (e: NioNoSuchFileException) {
        return
    }

    logger.info("Migrating prefix storage from $oldVersion to $newVersion.")

    val newData = convertPrefixData(
        prefixDataText = prefixDataText,
        oldVersion = oldVersion,
        newVersion = newVersion,
    )

    Files.writeString(storagePath, newData, PREFIX_FILE_CHARSET)
}
