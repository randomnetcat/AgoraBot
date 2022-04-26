package org.randomcat.agorabot.guild_state.feature.impl

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.guild_state.feature.GuildStateStorageTag
import org.randomcat.agorabot.guild_state.impl.JsonGuildStateMap
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
                    context.alwaysCloseObject(
                        {
                            JsonGuildStateMap(config.storageDir, context.configPersistService)
                        },
                        {
                            it.close()
                        },
                    )
                })

                return FeatureQueryResult.NotFound
            }
        }
    }
}
