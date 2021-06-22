package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.util.DiscordMessage

interface SecretHitlerCommandContext {
    fun respond(message: String)
    fun respond(message: DiscordMessage)
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
