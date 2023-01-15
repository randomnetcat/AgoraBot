package org.randomcat.agorabot.guild_state.feature.impl

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.guild_state.feature.GuildStateStorageTag
import org.randomcat.agorabot.guild_state.impl.JsonGuildStateMap
import java.nio.file.Path

private data class GuildStateStorageConfig(
    val storageDir: Path,
)

private fun readConfig(context: FeatureSetupContext): GuildStateStorageConfig {
    return GuildStateStorageConfig(storageDir = context.paths.storagePath.resolve("guild_storage"))
}

private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun guildStateStorageFactory(): FeatureSource<*> = FeatureSource.ofCloseable(
    name = "guild_state_storage_default",
    readConfig = ::readConfig,
    element = GuildStateStorageTag,
    dependencies = listOf(persistServiceDep),
    create = { config, context ->
        JsonGuildStateMap(config.storageDir, context[persistServiceDep])
    },
    close = { it.close() },
)
