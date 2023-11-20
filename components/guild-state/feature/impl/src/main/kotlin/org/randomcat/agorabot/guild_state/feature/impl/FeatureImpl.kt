package org.randomcat.agorabot.guild_state.feature.impl

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSetupContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.guild_state.feature.GuildStateStorageTag
import org.randomcat.agorabot.guild_state.feature.UserStateStorageTag
import org.randomcat.agorabot.guild_state.impl.JsonGuildStateMap
import org.randomcat.agorabot.guild_state.impl.JsonUserStateMap
import java.nio.file.Path

private data class KeyedStateStorageConfig(
    val storageDir: Path,
)

private fun readConfig(
    type: String,
    context: FeatureSetupContext,
): KeyedStateStorageConfig {
    return KeyedStateStorageConfig(storageDir = context.paths.storagePath.resolve("${type}_storage"))
}

private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)

@FeatureSourceFactory
fun guildStateStorageFactory(): FeatureSource<*> = FeatureSource.ofCloseable(
    name = "guild_state_storage_default",
    readConfig = { readConfig("guild", it) },
    element = GuildStateStorageTag,
    dependencies = listOf(persistServiceDep),
    create = { config, context ->
        JsonGuildStateMap(config.storageDir, context[persistServiceDep])
    },
    close = { it.close() },
)

@FeatureSourceFactory
fun userStateStorageFactory(): FeatureSource<*> = FeatureSource.ofCloseable(
    name = "user_state_storage_default",
    readConfig = { readConfig("user", it) },
    element = UserStateStorageTag,
    dependencies = listOf(persistServiceDep),
    create = { config, context ->
        JsonUserStateMap(config.storageDir, context[persistServiceDep])
    },
    close = { it.close() },
)
