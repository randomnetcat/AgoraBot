package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
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

class JsonPermissionMap(private val storagePath: Path) : MutablePermissionMap {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun readFromFile(path: Path): PersistentMap<PermissionPath, PersistentMap<String, BotPermissionState>> {
            if (Files.notExists(path)) return persistentMapOf()

            // Convert to simpler type for storage
            // Format: map of (permission path joined by PERMISSION_PATH_SEPARATOR) to (
            //     map of (entity id) to (enum name of BotPermissionState)
            // )
            val stored = Json.decodeFromString<Map<String, Map<String, String>>>(Files.readString(path, FILE_CHARSET))

            return stored
                .asIterable()
                .associate { (permissionPath, idMap) ->
                    PermissionPath.fromSplitting(permissionPath) to
                            idMap.mapValues { (_, state) -> BotPermissionState.valueOf(state) }.toPersistentMap()
                }
                .toPersistentMap()
        }

        private fun writeToFile(path: Path, value: Map<PermissionPath, Map<String, BotPermissionState>>) {
            val converted = value.asIterable().associate { (permissionPath, idMap) ->
                permissionPath.joinToString() to
                        idMap.mapValues { (_, state) -> state.name }
            }

            val content = Json.encodeToString<Map<String, Map<String, String>>>(converted)

            withTempFile { tempFile ->
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

    private val map: AtomicReference<PersistentMap<PermissionPath, PersistentMap<String, BotPermissionState>>> =
        AtomicReference(readFromFile(storagePath))

    fun schedulePersistenceOn(persistenceService: ConfigPersistService) {
        persistenceService.schedulePersistence({ map.get() }, { writeToFile(storagePath, it) })
    }

    override fun stateForId(path: PermissionPath, id: String): BotPermissionState? {
        return map.get().get(path)?.get(id)
    }

    override fun setStateForId(path: PermissionPath, id: String, newState: BotPermissionState) {
        map.updateAndGet { immutablePermissionsMap ->
            immutablePermissionsMap.mutate { permissionsMap ->
                val immutableIdMap = permissionsMap.getOrDefault(path, persistentMapOf())
                permissionsMap[path] = immutableIdMap.mutate { idMap ->
                    idMap[id] = newState
                }
            }
        }
    }
}

class JsonGuildPermissionMap(
    private val storageDir: Path,
    private val persistenceService: ConfigPersistService,
) : MutableGuildPermissionMap {
    init {
        Files.createDirectories(storageDir)
    }

    private class LoadOncePermissionMap(init: () -> MutablePermissionMap) {
        val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED, init)
    }

    private val map = AtomicReference<PersistentMap<String, LoadOncePermissionMap>>(persistentMapOf())

    override fun mapForGuild(guildId: String): MutablePermissionMap {
        run {
            val origMap = map.get()

            val existing = origMap[guildId]
            if (existing != null) return existing.value
        }

        return map.updateAndGet { origMap ->
            // If the map already contains the guild, don't add a new one - this would result in two
            // LoadOncePermissionMaps reaching the outside world.
            if (origMap.containsKey(guildId))
                origMap
            else
            // Only one JsonPermissionMap must be created (so it has exclusive ownership over the file). Only one
            // LoadOncePermissionMap will be returned to the outside world, and that one instance will lazy initialize
            // the one JsonPermissionMap when its value is accessed.
                origMap.put(guildId, LoadOncePermissionMap {
                    JsonPermissionMap(storagePath = storageDir.resolve(guildId))
                        .also { it.schedulePersistenceOn(persistenceService) }
                })
        }.getValue(guildId).value
    }
}
