package org.randomcat.agorabot.features

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.buttons.*
import org.randomcat.agorabot.secrethitler.context.SecretHitlerGameContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerMessageContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerNameContext
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerButtons
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerImpersonationMap
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.storage.feature.api.secretHitlerImpersonationMap
import org.randomcat.agorabot.secrethitler.storage.feature.api.secretHitlerRepostitory
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.asSnowflakeOrNull
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

private object NameContextImpl : SecretHitlerNameContext {
    override fun renderExternalName(name: SecretHitlerPlayerExternalName): String {
        val raw = name.raw

        return if (raw.toLongOrNull() != null) {
            "<@$raw>"
        } else {
            raw
        }
    }
}

private fun insertGameId(message: DiscordMessage, gameId: SecretHitlerGameId): DiscordMessage {
    val gameIdText = "Game id: ${gameId.raw}"

    return if (message.embeds.isNotEmpty()) {
        MessageBuilder(message).setEmbeds(message.embeds.map {
            val builder = EmbedBuilder(it)

            val footerText = it.footer?.text

            if (footerText.isNullOrBlank()) {
                builder.setFooter(gameIdText, it.footer?.iconUrl)
            } else {
                if (!footerText.contains(gameIdText)) {
                    builder.setFooter(footerText + "\n" + gameIdText, it.footer?.iconUrl)
                }
            }

            builder.build()
        }).build()
    } else {
        val builder = MessageBuilder(message)

        if (!builder.stringBuilder.contains(gameIdText)) {
            builder.stringBuilder.insert(0, gameIdText + "\n")
        }

        builder.build()
    }
}

private data class MessageEditQueueEntry(
    val targetMessage: DiscordMessage,
    val newContentBlock: () -> DiscordMessage?,
)

private class MessageContextImpl(
    private val impersonationMap: SecretHitlerImpersonationMap?,
    private val gameMessageChannel: MessageChannel,
    private val contextGameId: SecretHitlerGameId,
    private val editChannel: SendChannel<MessageEditQueueEntry>,
) : SecretHitlerMessageContext {
    override suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    ) {
        sendPrivateMessage(recipient, gameId, MessageBuilder(message).build())
    }

    private suspend fun sendRawPrivateMessage(recipientId: String, message: DiscordMessage) {
        gameMessageChannel.jda.openPrivateChannelById(recipientId).await().sendMessage(message).await()
    }

    override suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: DiscordMessage,
    ) {
        val rawName = recipient.raw

        val impersonationIds = impersonationMap?.dmUserIdsForName(rawName)

        val messageWithId = insertGameId(message, gameId)

        when {
            impersonationIds != null -> {
                val adjustedMessage =
                    MessageBuilder(messageWithId)
                        .also {
                            it.stringBuilder.insert(0, "Redirected from ${recipient.raw}:\n")
                        }
                        .build()

                for (userId in impersonationIds) {
                    sendRawPrivateMessage(userId, adjustedMessage)
                }
            }

            rawName.asSnowflakeOrNull() != null -> {
                sendRawPrivateMessage(rawName, messageWithId)
            }

            else -> {
                sendGameMessage("Unable to resolve user ${recipient.raw}")
            }
        }
    }

    override suspend fun sendGameMessage(message: DiscordMessage) {
        gameMessageChannel.sendMessage(insertGameId(message, contextGameId)).queue()
    }

    override suspend fun sendGameMessage(message: String) {
        gameMessageChannel.sendMessage("Game id: ${contextGameId}\n" + message).queue()
    }

    override suspend fun enqueueEditGameMessage(targetMessage: DiscordMessage, newContentBlock: () -> DiscordMessage?) {
        editChannel.send(MessageEditQueueEntry(targetMessage) {
            newContentBlock()?.let { insertGameId(it, gameId = contextGameId) }
        })
    }
}

private object NullMessageContext : SecretHitlerMessageContext {
    override suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    ) {
        // Intentionally do nothing.
    }

    override suspend fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: DiscordMessage,
    ) {
        // Intentionally do nothing.
    }

    override suspend fun sendGameMessage(message: String) {
        // Intentionally do nothing.
    }

    override suspend fun sendGameMessage(message: DiscordMessage) {
        // Intentionally do nothing.
    }

    override suspend fun enqueueEditGameMessage(targetMessage: DiscordMessage, newContentBlock: () -> DiscordMessage?) {
        // Intentionally do nothing.
    }
}

private val logger = LoggerFactory.getLogger("AgoraBotSecretHitlerFeature")

private suspend fun handleEditQueue(channel: ReceiveChannel<MessageEditQueueEntry>) {
    supervisorScope {
        val jobMap = mutableMapOf<String /* ChannelId */, Job>()
        var pruneCount = 0

        for (entry in channel) {
            try {
                ++pruneCount

                if (pruneCount == 100) {
                    pruneCount = 0
                    jobMap.entries.removeAll { it.value.isCompleted }
                }

                val targetChannelId = entry.targetMessage.channel.id

                val oldJob = jobMap[targetChannelId]

                jobMap[targetChannelId] = launch {
                    oldJob?.join()

                    val newContent = entry.newContentBlock()
                    if (newContent != null) {
                        entry.targetMessage.editMessage(newContent).await()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Error while processing update", e)
            }
        }
    }
}
private object SecretHitlerEditQueueChannelCacheKey

private fun secretHitlerFeature() = object : AbstractFeature() {
    private val nameContext = NameContextImpl
    private val FeatureContext.editQueueChannel
        get() = cache(SecretHitlerEditQueueChannelCacheKey) {
            val channel = alwaysCloseObject(
                {
                    Channel<MessageEditQueueEntry>()
                },
                {
                    it.cancel()
                },
            )

            coroutineScope.launch {
                handleEditQueue(channel)
            }

            channel
        }

    private fun makeMessageContext(
        impersonationMap: SecretHitlerImpersonationMap?,
        editQueueChannel: SendChannel<MessageEditQueueEntry>,
        jda: JDA,
        gameMessageChannelId: String?,
        gameId: SecretHitlerGameId,
    ): SecretHitlerMessageContext {
        val gameChannel = gameMessageChannelId?.let { jda.getChannelById(MessageChannel::class.java, it) }

        return if (gameChannel != null) {
            MessageContextImpl(
                impersonationMap = impersonationMap,
                gameMessageChannel = gameChannel,
                contextGameId = gameId,
                editChannel = editQueueChannel,
            )
        } else {
            NullMessageContext
        }
    }

    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        val repository = context.secretHitlerRepostitory
        val impersonationMap = context.secretHitlerImpersonationMap

        return mapOf(
            "secret_hitler" to SecretHitlerCommand(
                context.defaultCommandStrategy,
                repository = repository,
                impersonationMap = impersonationMap,
                nameContext = nameContext,
                makeMessageContext = { gameId, gameMessageChannel ->
                    MessageContextImpl(
                        impersonationMap = impersonationMap,
                        gameMessageChannel = gameMessageChannel,
                        contextGameId = gameId,
                        editChannel = context.editQueueChannel,
                    )
                },
            ),
        )
    }

    override fun buttonData(context: FeatureContext): FeatureButtonData {
        val repository = context.secretHitlerRepostitory
        val impersonationMap = context.secretHitlerImpersonationMap
        val editQueueChannel = context.editQueueChannel

        fun interactionContextFor(
            context: ButtonHandlerContext,
            gameId: SecretHitlerGameId,
        ): SecretHitlerInteractionContext {
            val gameMessageChannelId = repository.channelGameMap.channelIdByGame(gameId)

            return object :
                SecretHitlerInteractionContext,
                SecretHitlerGameContext,
                SecretHitlerNameContext by nameContext,
                SecretHitlerMessageContext by makeMessageContext(
                    impersonationMap = impersonationMap,
                    jda = context.event.jda,
                    gameMessageChannelId = gameMessageChannelId,
                    gameId = gameId,
                    editQueueChannel = editQueueChannel,
                ) {
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

        fun <Button : SecretHitlerButtonRequestDescriptor> ButtonHandlersReceiver.registerSH(
            type: KClass<Button>,
            handler: suspend (SecretHitlerRepository, SecretHitlerInteractionContext, ButtonInteractionEvent, Button) -> Unit,
        ) {
            withTypeImpl(type) { context, request ->
                handler(repository, interactionContextFor(context, request.gameId), context.event, request)
            }
        }

        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                registerSH(
                    SecretHitlerJoinGameButtonDescriptor::class,
                    SecretHitlerButtons::handleJoin,
                )

                registerSH(
                    SecretHitlerLeaveGameButtonDescriptor::class,
                    SecretHitlerButtons::handleLeave,
                )

                registerSH(
                    SecretHitlerChancellorCandidateSelectionButtonDescriptor::class,
                    SecretHitlerButtons::handleChancellorSelection,
                )

                registerSH(
                    SecretHitlerVoteButtonDescriptor::class,
                    SecretHitlerButtons::handleVote,
                )

                registerSH(
                    SecretHitlerPresidentPolicyChoiceButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentPolicySelection,
                )

                registerSH(
                    SecretHitlerChancellorPolicyChoiceButtonDescriptor::class,
                    SecretHitlerButtons::handleChancellorPolicySelection,
                )

                registerSH(
                    SecretHitlerPendingInvestigatePartySelectionButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentInvestigatePowerSelection,
                )

                registerSH(
                    SecretHitlerPendingSpecialElectionSelectionButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentSpecialElectionPowerSelection,
                )

                registerSH(
                    SecretHitlerPendingExecutionSelectionButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentExecutePowerSelection,
                )

                registerSH(
                    SecretHitlerChancellorRequestVetoButtonDescriptor::class,
                    SecretHitlerButtons::handleChancellorVetoRequest,
                )

                registerSH(
                    SecretHitlerPresidentAcceptVetoButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentVetoApproval,
                )

                registerSH(
                    SecretHitlerPresidentRejectVetoButtonDescriptor::class,
                    SecretHitlerButtons::handlePresidentVetoRejection,
                )
            },
        )
    }
}

@FeatureSourceFactory
fun secretHitlerFactory() = FeatureSource.ofConstant("secret_hitler", secretHitlerFeature())
