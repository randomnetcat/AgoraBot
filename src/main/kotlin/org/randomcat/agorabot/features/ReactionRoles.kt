package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.ReactionRolesCommand
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.guild_state.feature.GuildStateStorageTag
import org.randomcat.agorabot.reactionroles.GuildStateReactionRolesMap
import org.randomcat.agorabot.reactionroles.reactionRolesListener

private val strategyDep = FeatureDependency.Single(BaseCommandStrategyTag)
private val guildStateMapDep = FeatureDependency.Single(GuildStateStorageTag)

@FeatureSourceFactory
fun reactionRolesFactory() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "reaction_roles"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(strategyDep, guildStateMapDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BotCommandListTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val guildStateMap = context[guildStateMapDep]
        val strategy = context[strategyDep]

        val reactionRolesMap = GuildStateReactionRolesMap { guildId -> guildStateMap.stateForGuild(guildId) }
        val commands = mapOf("reactionroles" to ReactionRolesCommand(strategy, reactionRolesMap))
        val listener = reactionRolesListener(reactionRolesMap)

        return object : Feature {
            override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                if (tag is BotCommandListTag) return tag.values(commands)
                if (tag is JdaListenerTag) return tag.values(listener)

                invalidTag(tag)
            }
        }
    }
}
