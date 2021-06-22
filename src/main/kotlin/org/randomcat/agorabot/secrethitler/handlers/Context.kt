package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage

interface SecretHitlerCommandContext {
    fun respond(message: String)
    fun respond(message: DiscordMessage)
}

interface SecretHitlerNameContext {
    fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName
}

fun SecretHitlerCommandContext(
    commandReceiver: BaseCommandExecutionReceiverGuilded,
): SecretHitlerCommandContext {
    return object : SecretHitlerCommandContext {
        override fun respond(message: DiscordMessage) {
            commandReceiver.respond(message)
        }

        override fun respond(message: String) {
            commandReceiver.respond(message)
        }
    }
}
