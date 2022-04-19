package org.randomcat.agorabot.guild_state.impl

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.putAll
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.guild_state.GuildState
import org.randomcat.agorabot.guild_state.GuildStateMap
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import java.nio.file.Files
import java.nio.file.Path

class JsonGuildState(
    storagePath: Path,
    persistService: ConfigPersistService,
) : GuildState {
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
        persistService = persistService,
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

    fun close() {
        storage.close()
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
            JsonGuildState(storagePath = storageDirectory.resolve(guildId), persistService = persistService)
        }
    }

    fun close() {
        map.closeAndTake().values.forEach { it.close() }
    }
}
