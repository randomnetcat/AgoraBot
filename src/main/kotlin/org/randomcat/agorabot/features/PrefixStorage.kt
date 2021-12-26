package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.*
import org.randomcat.agorabot.setup.BotDataPaths
import java.nio.file.Path

private fun BotDataPaths.prefixStoragePath(): Path {
    return storagePath.resolve("prefixes")
}

private val PREFIX_STORAGE_CURRENT_VERSION = PrefixStorageVersion.JSON_MANY_PREFIX
private const val PREFIX_STORAGE_COMPONENT = "prefix_storage"

private data class PrefixStorageConfig(
    val prefixStoragePath: Path,
)

private object PrefixStorageMapCacheKey

@FeatureSourceFactory
fun prefixStorageFactory() = object : FeatureSource {
    override val featureName: String
        get() = "prefix_storage_default"

    override fun readConfig(context: FeatureSetupContext): PrefixStorageConfig {
        return PrefixStorageConfig(prefixStoragePath = context.paths.prefixStoragePath())
    }

    override fun createFeature(config: Any?): Feature {
        val typedConfig = config as PrefixStorageConfig
        val prefixStoragePath = typedConfig.prefixStoragePath

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is PrefixStorageTag) return tag.result(context.cache(PrefixStorageMapCacheKey) {
                    val versioningStorage = context.versioningStorage

                    migratePrefixStorage(
                        storagePath = prefixStoragePath,
                        oldVersion = versioningStorage.versionFor(PREFIX_STORAGE_COMPONENT)
                            ?.let { PrefixStorageVersion.valueOf(it) }
                            ?: PrefixStorageVersion.JSON_SINGLE_PREFIX,
                        newVersion = PREFIX_STORAGE_CURRENT_VERSION,
                    )

                    versioningStorage.setVersion(PREFIX_STORAGE_COMPONENT, PREFIX_STORAGE_CURRENT_VERSION.name)

                    JsonPrefixMap(default = ".", prefixStoragePath).apply {
                        schedulePersistenceOn(context.configPersistService)
                    }
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
