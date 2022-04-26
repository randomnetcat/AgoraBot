package org.randomcat.agorabot.config

import kotlinx.collections.immutable.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.NoSuchFileException as NioNoSuchFileException

private val PREFIX_FILE_CHARSET = Charsets.UTF_8

class JsonPrefixMap(
    private val default: String,
    storagePath: Path,
    persistService: ConfigPersistService,
) : MutableGuildPrefixMap {
    private object StrategyImpl : StorageStrategy<PersistentMap<String, PersistentList<String>>> {
        override fun defaultValue(): PersistentMap<String, PersistentList<String>> {
            return persistentMapOf()
        }

        override fun encodeToString(value: PersistentMap<String, PersistentList<String>>): String {
            return Json.encodeToString<Map<String, List<String>>>(value)
        }

        override fun decodeFromString(text: String): PersistentMap<String, PersistentList<String>> {
            return Json
                .decodeFromString<Map<String, List<String>>>(text)
                .mapValues { (_, v) -> v.toPersistentList() }
                .toPersistentMap()
        }
    }


    private val storage = AtomicCachedStorage(storagePath, StrategyImpl, persistService)

    private val defaultList = persistentListOf(default)

    override fun addPrefixForGuild(guildId: String, prefix: String) {
        storage.updateValue { oldMap ->
            val old = oldMap.getOrDefault(guildId, defaultList)
            // Put the longest prefixes first, so if we have overlapping prefixes--say "please"
            // and "please please", both "please cfj" and "please please cfj" work as expected
            oldMap.put(guildId, old.add(prefix).mutate { mutator -> mutator.sortByDescending { it.length } })
        }
    }

    override fun removePrefixForGuild(guildId: String, prefix: String) {
        storage.updateValue { oldMap ->
            val old = oldMap.getOrDefault(guildId, defaultList)
            oldMap.put(guildId, old.removeAll { it == prefix })
        }
    }

    override fun clearPrefixesForGuild(guildId: String) {
        storage.updateValue { oldMap ->
            oldMap.put(guildId, persistentListOf())
        }
    }

    override fun prefixesForGuild(guildId: String): List<String> {
        return storage.getValue().getOrDefault(guildId, defaultList)
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
