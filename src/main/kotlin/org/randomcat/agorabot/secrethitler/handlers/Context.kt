package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import java.time.Duration

interface SecretHitlerButtonContext {
    fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String
}

interface SecretHitlerMessageContext {
    fun sendGameMessage(message: String)
    fun sendGameMessage(message: DiscordMessage)

    fun sendPrivateMessage(recipient: SecretHitlerPlayerExternalName, message: String)
    fun sendPrivateMessage(recipient: SecretHitlerPlayerExternalName, message: DiscordMessage)
}

interface SecretHitlerGameContext : SecretHitlerButtonContext, SecretHitlerMessageContext

interface SecretHitlerCommandContext : SecretHitlerGameContext {
    fun respond(message: String)
    fun respond(message: DiscordMessage)
}

interface SecretHitlerInteractionContext {
    fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName
}
