package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.util.AtomicLoadOnceMap
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

    override fun stateForId(path: PermissionPath, id: PermissionMapId): BotPermissionState? {
        return map.get().get(path)?.get(id.raw)
    }

    override fun setStateForId(path: PermissionPath, id: PermissionMapId, newState: BotPermissionState) {
        map.updateAndGet { immutablePermissionsMap ->
            immutablePermissionsMap.mutate { permissionsMap ->
                val immutableIdMap = permissionsMap.getOrDefault(path, persistentMapOf())
                permissionsMap[path] = immutableIdMap.mutate { idMap ->
                    idMap[id.raw] = newState
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

    private val map = AtomicLoadOnceMap<String /* GuildId */, MutablePermissionMap>()

    override fun mapForGuild(guildId: String): MutablePermissionMap {
        return map.getOrPut(guildId) {
            JsonPermissionMap(storagePath = storageDir.resolve(guildId))
                .also { it.schedulePersistenceOn(persistenceService) }
        }
    }
}
