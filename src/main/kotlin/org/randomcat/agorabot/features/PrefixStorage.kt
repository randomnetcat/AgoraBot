package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
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

private val versioningStorageDep = FeatureDependency.Single(VersioningStorageTag)
private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun prefixStorageFactory(): FeatureSource<*> = object : FeatureSource<PrefixStorageConfig> {
    override val featureName: String
        get() = "prefix_storage_default"

    override fun readConfig(context: FeatureSetupContext): PrefixStorageConfig {
        return PrefixStorageConfig(prefixStoragePath = context.paths.prefixStoragePath())
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(versioningStorageDep, persistServiceDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(PrefixStorageTag)

    override fun createFeature(config: PrefixStorageConfig, context: FeatureSourceContext): Feature {
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

        val map = JsonPrefixMap(default = "!", config.prefixStoragePath, persistService)
        return Feature.singleTag(PrefixStorageTag, map)
    }
}
