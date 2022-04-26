package org.randomcat.agorabot.features

import org.randomcat.agorabot.AbstractFeature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.ReactionRolesCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.guild_state.feature.guildStateMap
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.reactionroles.GuildStateReactionRolesMap
import org.randomcat.agorabot.reactionroles.reactionRolesListener

private object ReactionRolesStorageCacheKey

private val FeatureContext.reactionRolesMap
    get() = cache(ReactionRolesStorageCacheKey) {
        GuildStateReactionRolesMap { guildId -> guildStateMap.stateForGuild(guildId) }
    }

@FeatureSourceFactory
fun reactionRolesFactory() = FeatureSource.ofConstant("reaction_roles", object : AbstractFeature() {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf("reactionroles" to ReactionRolesCommand(context.defaultCommandStrategy, context.reactionRolesMap))
    }

    override fun jdaListeners(context: FeatureContext): List<Any> {
        return listOf(reactionRolesListener(context.reactionRolesMap))
    }
})
