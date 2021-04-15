package org.randomcat.agorabot.features

import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.commands.ReactionRolesCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.reactionroles.MutableReactionRolesMap
import org.randomcat.agorabot.reactionroles.reactionRolesListener

fun reactionRolesFeature(reactionRolesMap: MutableReactionRolesMap) = object : Feature {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf("reactionroles" to ReactionRolesCommand(context.defaultCommandStrategy, reactionRolesMap))
    }

    override fun registerListenersTo(jda: JDA) {
        jda.addEventListener(reactionRolesListener(reactionRolesMap))
    }
}
