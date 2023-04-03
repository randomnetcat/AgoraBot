package org.randomcat.agorabot.versioning_storage.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.versioning_storage.feature.api.VersioningStorageTag
import org.randomcat.agorabot.versioning_storage.impl.JsonVersioningStorage
import java.nio.file.Path

private data class VersioningStorageConfig(
    val versioningStoragePath: Path,
)

@FeatureSourceFactory
fun versioningStorageFactory(): FeatureSource<*> = object : FeatureSource<VersioningStorageConfig> {
    override val featureName: String
        get() = "versioning_storage_default"

    override fun readConfig(context: FeatureSetupContext): VersioningStorageConfig {
        return VersioningStorageConfig(versioningStoragePath = context.paths.storagePath.resolve("storage_versions"))
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = emptyList()

    override val provides: List<FeatureElementTag<*>>
        get() = emptyList()

    override fun createFeature(config: VersioningStorageConfig, context: FeatureSourceContext): Feature {
        val versioningStoragePath = config.versioningStoragePath

        return Feature.singleTag(VersioningStorageTag, JsonVersioningStorage(versioningStoragePath))
    }
}
