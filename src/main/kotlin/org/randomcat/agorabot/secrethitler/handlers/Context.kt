package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.commands.impl.currentChannel
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import java.time.Duration

interface SecretHitlerButtonContext {
    fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String
}

interface SecretHitlerMessageContext {
    fun sendGameMessage(message: String)
    fun sendGameMessage(message: DiscordMessage)
}

interface SecretHitlerGameContext : SecretHitlerButtonContext, SecretHitlerMessageContext

interface SecretHitlerCommandContext : SecretHitlerGameContext {
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
        override fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String {
            return commandReceiver.newButtonId(descriptor, expiryDuration)
        }

        // Assume that commands are only sent in the same channel as the game.
        override fun sendGameMessage(message: String) {
            commandReceiver.currentChannel().sendMessage(message).queue()
        }

        // Assume that commands are only sent in the same channel as the game.
        override fun sendGameMessage(message: DiscordMessage) {
            commandReceiver.currentChannel().sendMessage(message).queue()
        }

        override fun respond(message: DiscordMessage) {
            commandReceiver.respond(message)
        }

        override fun respond(message: String) {
            commandReceiver.respond(message)
        }
    }
}
