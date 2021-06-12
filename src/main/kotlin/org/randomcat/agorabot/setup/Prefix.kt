package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import java.nio.file.Path

private fun BotDataPaths.prefixStoragePath(): Path {
    return storagePath.resolve("prefixes")
}

private val PREFIX_STORAGE_CURRENT_VERSION = PrefixStorageVersion.JSON_MANY_PREFIX
private const val PREFIX_STORAGE_COMPONENT = "prefix_storage"

fun setupPrefixStorage(
    paths: BotDataPaths,
    versioningStorage: VersioningStorage,
    persistService: ConfigPersistService,
): MutableGuildPrefixMap {
    val prefixStoragePath = paths.prefixStoragePath()

    migratePrefixStorage(
        storagePath = prefixStoragePath,
        oldVersion = versioningStorage.versionFor(PREFIX_STORAGE_COMPONENT)
            ?.let { PrefixStorageVersion.valueOf(it) }
            ?: PrefixStorageVersion.JSON_SINGLE_PREFIX,
        newVersion = PREFIX_STORAGE_CURRENT_VERSION,
    )

    versioningStorage.setVersion(PREFIX_STORAGE_COMPONENT, PREFIX_STORAGE_CURRENT_VERSION.name)

    return JsonPrefixMap(default = ".", prefixStoragePath).apply { schedulePersistenceOn(persistService) }
}
