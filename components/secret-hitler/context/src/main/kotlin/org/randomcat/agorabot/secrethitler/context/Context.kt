package org.randomcat.agorabot.secrethitler.context

import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import java.time.Duration

interface SecretHitlerButtonContext {
    fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String
    fun invalidButtonId(): String
}

interface SecretHitlerPrivateMessageContext {
    suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    )

    suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: MessageCreateData,
    )
}

interface SecretHitlerGameMessageContext {
    suspend fun sendGameMessage(message: String)
    suspend fun sendGameMessage(message: MessageCreateData)

    /**
     * Enqueues the editing of the target game message to have the content produced by the provided function.
     * This only enqueues the editing in order to avoid race conditions between multiple updates to the same message.
     * If the block returns null, the message will not be edited.
     *
     * If multiple updates to the same message are enqueued in a racy fashion, it is unspecified which one will be
     * the final state of the message. In general, the final state should be sent in a separate message that is never
     * edited, to ensure that it is recorded.
     */
    suspend fun enqueueEditGameMessage(
        targetMessage: DiscordMessage,
        newContentBlock: () -> MessageCreateData?,
    )
}

interface SecretHitlerMessageContext : SecretHitlerPrivateMessageContext, SecretHitlerGameMessageContext

interface SecretHitlerNameContext {
    fun renderExternalName(name: SecretHitlerPlayerExternalName): String
}

interface SecretHitlerGameContext : SecretHitlerButtonContext, SecretHitlerMessageContext, SecretHitlerNameContext

interface SecretHitlerCommandContext : SecretHitlerGameContext {
    suspend fun respond(message: String)
    suspend fun respond(message: MessageCreateData)
}

interface SecretHitlerInteractionContext : SecretHitlerGameContext {
    fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName
}
