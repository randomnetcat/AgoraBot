package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.config.JsonPrefixMap
import org.randomcat.agorabot.config.PrefixStorageTag
import org.randomcat.agorabot.config.PrefixStorageVersion
import org.randomcat.agorabot.config.migratePrefixStorage
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.setup.BotDataPaths
import org.randomcat.agorabot.versioning_storage.feature.api.VersioningStorageTag
import java.nio.file.Path

private fun BotDataPaths.prefixStoragePath(): Path {
    return storagePath.resolve("prefixes")
}

private val PREFIX_STORAGE_CURRENT_VERSION = PrefixStorageVersion.JSON_MANY_PREFIX
private const val PREFIX_STORAGE_COMPONENT = "prefix_storage"

private data class PrefixStorageConfig(
    val prefixStoragePath: Path,
)

private fun readConfig(context: FeatureSetupContext): PrefixStorageConfig {
    return PrefixStorageConfig(prefixStoragePath = context.paths.prefixStoragePath())
}

private val versioningStorageDep = FeatureDependency.Single(VersioningStorageTag)
private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun prefixStorageFactory(): FeatureSource<*> = FeatureSource.ofCloseable(
    name = "prefix_storage_default",
    element = PrefixStorageTag,
    dependencies = listOf(versioningStorageDep, persistServiceDep),
    readConfig = ::readConfig,
    create = { config, context ->
        val versioningStorage = context[versioningStorageDep]
        val persistService = context[persistServiceDep]

        migratePrefixStorage(
            storagePath = config.prefixStoragePath,
            oldVersion = versioningStorage.versionFor(PREFIX_STORAGE_COMPONENT)
                ?.let { PrefixStorageVersion.valueOf(it) }
                ?: PrefixStorageVersion.JSON_SINGLE_PREFIX,
            newVersion = PREFIX_STORAGE_CURRENT_VERSION,
        )

        versioningStorage.setVersion(PREFIX_STORAGE_COMPONENT, PREFIX_STORAGE_CURRENT_VERSION.name)

        JsonPrefixMap(default = "!", config.prefixStoragePath, persistService)
    },
    close = { it.close() }
)
