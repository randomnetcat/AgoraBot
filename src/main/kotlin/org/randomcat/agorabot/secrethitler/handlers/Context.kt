package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import java.time.Duration

interface SecretHitlerButtonContext {
    fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String
}

interface SecretHitlerPrivateMessageContext {
    fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    )

    fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: DiscordMessage,
    )
}

interface SecretHitlerGameMessageContext {
    fun sendGameMessage(message: String)
    fun sendGameMessage(message: DiscordMessage)
}

interface SecretHitlerMessageContext : SecretHitlerPrivateMessageContext, SecretHitlerGameMessageContext

interface SecretHitlerNameContext {
    fun renderExternalName(name: SecretHitlerPlayerExternalName): String
}

interface SecretHitlerGameContext : SecretHitlerButtonContext, SecretHitlerMessageContext, SecretHitlerNameContext

interface SecretHitlerCommandContext : SecretHitlerGameContext {
    suspend fun respond(message: String)
    suspend fun respond(message: DiscordMessage)
}

interface SecretHitlerInteractionContext : SecretHitlerGameContext {
    fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName
}
