package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.base.requirements.discord_ext.GuildStateStrategy
import org.randomcat.agorabot.commands.base.requirements.discord_ext.GuildStateStrategyTag
import org.randomcat.agorabot.guild_state.GuildState
import org.randomcat.agorabot.guild_state.GuildStateMap
import org.randomcat.agorabot.guild_state.feature.GuildStateStorageTag

private fun makeGuildStateStrategy(guildStateMap: GuildStateMap): GuildStateStrategy {
    return object : GuildStateStrategy {
        override fun guildStateFor(guildId: String): GuildState {
            return guildStateMap.stateForGuild(guildId)
        }
    }
}

private val guildStateDep = FeatureDependency.Single(GuildStateStorageTag)

@FeatureSourceFactory
fun baseCommandGuildStateSource() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "base_command_guild_state_default"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(guildStateDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BaseCommandDependencyTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val stateMap = context[guildStateDep]
        val strategy = makeGuildStateStrategy(stateMap)

        return Feature.singleTag(
            BaseCommandDependencyTag,
            BaseCommandDependencyResult(
                baseTag = GuildStateStrategyTag,
                value = strategy,
            ),
        )
    }
}
