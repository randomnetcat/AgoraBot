package org.randomcat.agorabot.config

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.putAll
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

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
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun readFromFile(path: Path): PersistentMap<String, String> {
            if (Files.notExists(path)) return persistentMapOf()

            val content = Files.readString(path, FILE_CHARSET)
            return Json.decodeFromString<Map<String, String>>(content).toPersistentMap()
        }

        private fun writeToFile(path: Path, data: Map<String, String>) {
            withTempFile { tempFile ->
                Files.writeString(
                    tempFile,
                    Json.encodeToString<Map<String, String>>(data),
                    FILE_CHARSET,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )

                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private val data: AtomicReference<PersistentMap<String, String>> = AtomicReference(readFromFile(storagePath))

    override fun getStrings(keys: List<String>): List<String?> {
        val currentData = data.get()
        return keys.map { currentData[it] }
    }

    override fun getString(key: String): String? {
        return data.get()[key]
    }

    override fun setStrings(keys: List<String>, values: List<String>) {
        require(keys.size == values.size)
        val pairs = keys.zip(values)

        data.updateAndGet {
            it.putAll(pairs)
        }
    }

    override fun setString(key: String, value: String) {
        data.updateAndGet {
            it.put(key, value)
        }
    }

    override fun updateStrings(keys: List<String>, mapper: (old: List<String?>) -> List<String>) {
        data.updateAndGet { currentData ->
            keys
                .map { currentData[it] }
                .let(mapper)
                .also { require(it.size == keys.size) }
                .let { keys.zip(it) }
                .let { currentData.putAll(it) }
        }
    }

    override fun updateString(key: String, mapper: (old: String?) -> String) {
        data.updateAndGet {
            it.put(key, mapper(it[key]))
        }
    }

    fun schedulePersistenceOn(service: ConfigPersistService) {
        service.schedulePersistence({ data.get() }, { writeToFile(storagePath, it) })
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
