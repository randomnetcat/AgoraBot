package org.randomcat.agorabot.config

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

interface GuildState {
    fun getString(key: String): String?
    fun setString(key: String, value: String)

    fun updateString(key: String, mapper: (old: String?) -> String)
}

inline fun <reified T> GuildState.get(key: String): T? {
    return getString(key)?.let { Json.decodeFromString<T>(it) }
}

inline fun <reified T> GuildState.set(key: String, value: T) {
    setString(key, Json.encodeToString<T>(value))
}

inline fun <reified T> GuildState.update(key: String, crossinline mapper: (old: T?) -> T) {
    updateString(key) { oldString ->
        Json.encodeToString<T>(mapper(oldString?.let { Json.decodeFromString<T>(it) }))
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
                    Charsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )

                Files.move(tempFile, path)
            }
        }
    }

    private val data: AtomicReference<PersistentMap<String, String>> = AtomicReference(readFromFile(storagePath))

    override fun getString(key: String): String? {
        return data.get()[key]
    }

    override fun setString(key: String, value: String) {
        data.updateAndGet {
            it.put(key, value)
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
    private val map = AtomicLoadOnceMap<String /* GuildId */, JsonGuildState>()

    override fun stateForGuild(guildId: String): GuildState {
        return map.getOrPut(guildId) {
            JsonGuildState(storagePath = storageDirectory.resolve(guildId))
                .also { it.schedulePersistenceOn(persistService) }
        }
    }
}
