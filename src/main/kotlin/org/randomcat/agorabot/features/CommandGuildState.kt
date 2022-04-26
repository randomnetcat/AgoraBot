package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.base.requirements.discord_ext.GuildStateStrategy
import org.randomcat.agorabot.commands.base.requirements.discord_ext.GuildStateStrategyTag
import org.randomcat.agorabot.guild_state.GuildState
import org.randomcat.agorabot.guild_state.GuildStateMap
import org.randomcat.agorabot.guild_state.feature.guildStateMap

private object GuildStateStrategyCacheKey

private fun makeGuildStateStrategy(guildStateMap: GuildStateMap): GuildStateStrategy {
    return object : GuildStateStrategy {
        override fun guildStateFor(guildId: String): GuildState {
            return guildStateMap.stateForGuild(guildId)
        }
    }
}

@FeatureSourceFactory
fun baseCommandGuildStateSource() = FeatureSource.ofConstant("base_command_guild_state_default", object : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is BaseCommandDependencyTag && tag.baseTag is GuildStateStrategyTag) {
            return tag.result(context.cache(GuildStateStrategyCacheKey) {
                makeGuildStateStrategy(context.guildStateMap)
            })
        }

        return FeatureQueryResult.NotFound
    }
})
