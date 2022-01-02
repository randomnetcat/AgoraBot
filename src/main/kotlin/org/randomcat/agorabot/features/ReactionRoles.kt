package org.randomcat.agorabot.features

import org.randomcat.agorabot.AbstractFeature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.commands.ReactionRolesCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.reactionroles.MutableReactionRolesMap
import org.randomcat.agorabot.reactionroles.reactionRolesListener

fun reactionRolesFeature(reactionRolesMap: MutableReactionRolesMap) = object : AbstractFeature() {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf("reactionroles" to ReactionRolesCommand(context.defaultCommandStrategy, reactionRolesMap))
    }

    override fun jdaListeners(): List<Any> {
        return listOf(reactionRolesListener(reactionRolesMap))
    }
}
