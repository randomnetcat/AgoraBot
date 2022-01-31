package org.randomcat.agorabot.config

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.putAll
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import java.nio.file.Files
import java.nio.file.Path

interface GuildState {
    fun getStrings(keys: List<String>): List<String?>
    fun getString(key: String): String? = getStrings(listOf(key)).single()

    fun setStrings(keys: List<String>, values: List<String>)
    fun setString(key: String, value: String) = setStrings(listOf(key), listOf(value))

    fun updateStrings(keys: List<String>, mapper: (old: List<String?>) -> List<String>)

    fun updateString(key: String, mapper: (old: String?) -> String) = updateStrings(listOf(key)) { values ->
        listOf(mapper(values.single()))
    }
}

inline fun <reified T> GuildState.get(key: String): T? {
    return getString(key)?.let { Json.decodeFromString<T>(it) }
}

inline fun <reified T> GuildState.getMany(keys: List<String>): List<T?> {
    return getStrings(keys).map { str -> str?.let { Json.decodeFromString<T>(it) } }
}

inline fun <reified T> GuildState.set(key: String, value: T) {
    setString(key, Json.encodeToString<T>(value))
}

inline fun <reified T> GuildState.setMany(keys: List<String>, values: List<T>) {
    setStrings(keys, values.map { Json.encodeToString<T>(it) })
}

inline fun <reified T> GuildState.update(key: String, crossinline mapper: (old: T?) -> T) {
    updateString(key) { oldString ->
        Json.encodeToString<T>(mapper(oldString?.let { Json.decodeFromString<T>(it) }))
    }
}

inline fun <reified T> GuildState.updateMany(keys: List<String>, crossinline mapper: (old: List<T?>) -> List<T>) {
    updateStrings(keys) { oldStrings ->
        mapper(
            oldStrings.map { oldString ->
                oldString?.let { Json.decodeFromString<T>(it) }
            }
        ).map {
            Json.encodeToString<T>(it)
        }
    }
}

interface GuildStateMap {
    fun stateForGuild(guildId: String): GuildState
}

class JsonGuildState(private val storagePath: Path) : GuildState {
    private object StrategyImpl : StorageStrategy<PersistentMap<String, String>> {
        override fun defaultValue(): PersistentMap<String, String> {
            return persistentMapOf()
        }

        override fun encodeToString(value: PersistentMap<String, String>): String {
            return Json.encodeToString<Map<String, String>>(value)
        }

        override fun decodeFromString(text: String): PersistentMap<String, String> {
            return Json.decodeFromString<Map<String, String>>(text).toPersistentMap()
        }
    }

    private val storage = AtomicCachedStorage(
        storagePath = storagePath,
        strategy = StrategyImpl,
    )

    override fun getStrings(keys: List<String>): List<String?> {
        val currentData = storage.getValue()
        return keys.map { currentData[it] }
    }

    override fun getString(key: String): String? {
        return storage.getValue()[key]
    }

    override fun setStrings(keys: List<String>, values: List<String>) {
        require(keys.size == values.size)
        val pairs = keys.zip(values)

        storage.updateValue {
            it.putAll(pairs)
        }
    }

    override fun setString(key: String, value: String) {
        storage.updateValue {
            it.put(key, value)
        }
    }

    override fun updateStrings(keys: List<String>, mapper: (old: List<String?>) -> List<String>) {
        storage.updateValue { currentData ->
            keys
                .map { currentData[it] }
                .let(mapper)
                .also { require(it.size == keys.size) }
                .let { keys.zip(it) }
                .let { currentData.putAll(it) }
        }
    }

    override fun updateString(key: String, mapper: (old: String?) -> String) {
        storage.updateValue {
            it.put(key, mapper(it[key]))
        }
    }

    fun schedulePersistenceOn(service: ConfigPersistService) {
        storage.schedulePersistenceOn(service)
    }
}

class JsonGuildStateMap(
    private val storageDirectory: Path,
    private val persistService: ConfigPersistService,
) : GuildStateMap {
    init {
        Files.createDirectories(storageDirectory)
    }

    private val map = AtomicLoadOnceMap<String /* GuildId */, JsonGuildState>()

    override fun stateForGuild(guildId: String): GuildState {
        return map.getOrPut(guildId) {
            JsonGuildState(storagePath = storageDirectory.resolve(guildId))
                .also { it.schedulePersistenceOn(persistService) }
        }
    }
}
