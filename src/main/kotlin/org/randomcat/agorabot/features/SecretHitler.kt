package org.randomcat.agorabot.features

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.SecretHitlerImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.*
import org.randomcat.agorabot.secrethitler.handlers.*
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.asSnowflakeOrNull
import org.randomcat.agorabot.util.handleTextResponse
import java.time.Duration
import java.time.Instant

private data class NameContextImpl(
    val impersonationMap: SecretHitlerImpersonationMap?,
) : SecretHitlerNameContext {
    override fun renderExternalName(name: SecretHitlerPlayerExternalName): String {
        val raw = name.raw

        return if (raw.toLongOrNull() != null) {
            "<@$raw>"
        } else {
            raw
        }
    }
}

private class MessageContextImpl(
    private val impersonationMap: SecretHitlerImpersonationMap?,
    private val gameMessageChannel: MessageChannel,
) : SecretHitlerMessageContext {
    override fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    ) {
        sendPrivateMessage(recipient, gameId, MessageBuilder(message).build())
    }

    private fun queuePrivateMessage(recipientId: String, message: DiscordMessage) {
        gameMessageChannel.jda.openPrivateChannelById(recipientId).queue { channel ->
            channel.sendMessage(message).queue()
        }
    }

    override fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: DiscordMessage,
    ) {
        val rawName = recipient.raw

        val impersonationIds = impersonationMap?.dmUserIdsForName(rawName)

        when {
            impersonationIds != null -> {
                val adjustedMessage =
                    MessageBuilder(message)
                        .also {
                            it.stringBuilder.insert(0, "Redirected from ${recipient.raw}:\n")
                        }
                        .build()

                for (userId in impersonationIds) {
                    queuePrivateMessage(userId, adjustedMessage)
                }
            }

            rawName.asSnowflakeOrNull() != null -> {
                queuePrivateMessage(rawName, message)
            }

            else -> {
                sendGameMessage("Unable to resolve user ${recipient.raw}")
            }
        }
    }

    override fun sendGameMessage(message: DiscordMessage) {
        gameMessageChannel.sendMessage(message).queue()
    }

    override fun sendGameMessage(message: String) {
        gameMessageChannel.sendMessage(message).queue()
    }
}

fun secretHitlerFeature(
    repository: SecretHitlerRepository,
    impersonationMap: SecretHitlerMutableImpersonationMap?,
) = object : Feature {
    private val nameContext = NameContextImpl(impersonationMap)

    private fun makeMessageContext(channel: MessageChannel): SecretHitlerMessageContext {
        return MessageContextImpl(
            impersonationMap = impersonationMap,
            gameMessageChannel = channel,
        )
    }

    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "secret_hitler" to SecretHitlerCommand(
                context.defaultCommandStrategy,
                repository = repository,
                impersonationMap = impersonationMap,
                nameContext = nameContext,
                makeMessageContext = this::makeMessageContext,
            ),
        )
    }

    override fun buttonData(): FeatureButtonData {
        fun interactionContextFor(context: ButtonHandlerContext): SecretHitlerInteractionContext {
            return object :
                SecretHitlerInteractionContext,
                SecretHitlerGameContext,
                SecretHitlerNameContext by nameContext,
                SecretHitlerMessageContext by makeMessageContext(channel = context.event.channel) {
                override fun newButtonId(descriptor: ButtonRequestDescriptor, expiryDuration: Duration): String {
                    return context.buttonRequestDataMap.putRequest(
                        data = ButtonRequestData(
                            descriptor = descriptor,
                            expiry = Instant.now().plus(expiryDuration),
                        )
                    ).raw
                }

                override fun nameFromInteraction(interaction: Interaction): SecretHitlerPlayerExternalName {
                    val userId = interaction.user.id
                    val effectiveName = impersonationMap?.currentNameForId(userId) ?: userId

                    return SecretHitlerPlayerExternalName(effectiveName)
                }
            }
        }

        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<SecretHitlerJoinGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleJoin(
                        repository = repository,
                        context = interactionContextFor(context),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerLeaveGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleLeave(
                        repository = repository,
                        context = interactionContextFor(context),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorCandidateSelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleChancellorSelection(
                        repository = repository,
                        context = interactionContextFor(context),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerVoteButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleVote(
                        repository = repository,
                        context = interactionContextFor(context),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPresidentPolicyChoiceButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentPolicySelection(
                        repository = repository,
                        context = interactionContextFor(context),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorPolicyChoiceButtonDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        "Not yet implemented"
                    }
                }

                withType<SecretHitlerPendingInvestigatePartySelectionButtonDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        "Not yet implemented"
                    }
                }

                withType<SecretHitlerPendingSpecialElectionSelectionButtonDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        "Not yet implemented"
                    }
                }

                withType<SecretHitlerPendingExecutionSelectionButtonDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        "Not yet implemented"
                    }
                }
            },
        )
    }
}
