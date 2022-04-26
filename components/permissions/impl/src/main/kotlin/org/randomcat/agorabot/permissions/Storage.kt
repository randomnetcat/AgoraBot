package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import java.nio.file.Files
import java.nio.file.Path

private typealias JsonValueType = PersistentMap<PermissionPath, PersistentMap<String, BotPermissionState>>
private typealias JsonStorageType = Map<String, Map<String, String>>

class JsonPermissionMap(storagePath: Path, persistService: ConfigPersistService) : MutablePermissionMap {
    private object Strategy : StorageStrategy<JsonValueType> {
        override fun defaultValue(): JsonValueType {
            return persistentMapOf()
        }

        private fun JsonStorageType.toValue(): JsonValueType {
            return asIterable()
                .associate { (permissionPath, idMap) ->
                    PermissionPath.fromSplitting(permissionPath) to
                            idMap.mapValues { (_, state) -> BotPermissionState.valueOf(state) }.toPersistentMap()
                }
                .toPersistentMap()
        }

        private fun JsonValueType.toStorageValue(): JsonStorageType {
            return asIterable().associate { (path, idMap) ->
                path.joinToString() to idMap.mapValues { (_, state) -> state.name }
            }
        }

        override fun encodeToString(value: JsonValueType): String {
            return Json.encodeToString<JsonStorageType>(value.toStorageValue())
        }

        override fun decodeFromString(text: String): JsonValueType {
            return Json.decodeFromString<JsonStorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage<JsonValueType>(
        storagePath = storagePath,
        strategy = Strategy,
        persistService = persistService,
    )

    override fun stateForId(path: PermissionPath, id: PermissionMapId): BotPermissionState? {
        return impl.getValue()[path]?.get(id.raw)
    }

    override fun listEntries(): Map<PermissionPath, Map<PermissionMapId, BotPermissionState>> {
        return impl.getValue().mapValues { (_, v) -> v.mapKeys { (id, _) -> PermissionMapId(id) } }
    }

    override fun setStateForId(path: PermissionPath, id: PermissionMapId, newState: BotPermissionState) {
        impl.updateValue { immutablePermissionsMap ->
            immutablePermissionsMap.mutate { permissionsMap ->
                val immutableIdMap = permissionsMap.getOrDefault(path, persistentMapOf())
                permissionsMap[path] = immutableIdMap.mutate { idMap ->
                    idMap[id.raw] = newState
                }
            }
        }
    }

    fun close() {
        impl.close()
    }
}

class JsonGuildPermissionMap(
    private val storageDir: Path,
    private val persistenceService: ConfigPersistService,
) : MutableGuildPermissionMap {
    init {
        Files.createDirectories(storageDir)
    }

    private val map = AtomicLoadOnceMap<String /* GuildId */, JsonPermissionMap>()

    override fun mapForGuild(guildId: String): MutablePermissionMap {
        return map.getOrPut(guildId) {
            JsonPermissionMap(storagePath = storageDir.resolve(guildId), persistService = persistenceService)
        }
    }

    fun close() {
        map.closeAndTake().values.forEach { it.close() }
    }
}
