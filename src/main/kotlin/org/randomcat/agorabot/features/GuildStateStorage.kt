package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.GuildStateStorageTag
import org.randomcat.agorabot.config.JsonGuildStateMap
import org.randomcat.agorabot.config.persist.feature.configPersistService
import java.nio.file.Path

private data class GuildStateStorageConfig(
    val storageDir: Path,
)

private object GuildStateMapCacheKey

@FeatureSourceFactory
fun guildStateStorageFactory() = object : FeatureSource {
    override val featureName: String
        get() = "guild_state_storage_default"

    override fun readConfig(context: FeatureSetupContext): GuildStateStorageConfig {
        return GuildStateStorageConfig(storageDir = context.paths.storagePath.resolve("guild_storage"))
    }

    override fun createFeature(config: Any?): Feature {
        config as GuildStateStorageConfig

        return object : Feature {
            override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
                if (tag is GuildStateStorageTag) return tag.result(context.cache(GuildStateMapCacheKey) {
                    JsonGuildStateMap(config.storageDir, context.configPersistService)
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
