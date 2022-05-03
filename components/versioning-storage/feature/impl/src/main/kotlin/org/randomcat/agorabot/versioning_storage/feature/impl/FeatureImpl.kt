package org.randomcat.agorabot.versioning_storage.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.versioning_storage.feature.api.VersioningStorageTag
import org.randomcat.agorabot.versioning_storage.impl.JsonVersioningStorage
import java.nio.file.Path

private data class VersioningStorageConfig(
    val versioningStoragePath: Path,
)

@FeatureSourceFactory
fun versioningStorageFactory() = object : FeatureSource {
    override val featureName: String
        get() = "versioning_storage_default"

    override fun readConfig(context: FeatureSetupContext): VersioningStorageConfig {
        return VersioningStorageConfig(versioningStoragePath = context.paths.storagePath.resolve("storage_versions"))
    }

    override fun createFeature(config: Any?): Feature {
        config as VersioningStorageConfig
        val versioningStoragePath = config.versioningStoragePath

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is VersioningStorageTag) return tag.result(JsonVersioningStorage(versioningStoragePath))
                return FeatureQueryResult.NotFound
            }
        }
    }
}
