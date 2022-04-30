package org.randomcat.agorabot.features

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.interactions.Interaction
import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.*
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.config.readConfigFromFile
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.*
import org.randomcat.agorabot.secrethitler.buttons.*
import org.randomcat.agorabot.secrethitler.handlers.*
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.setup.features.featureConfigDir
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.asSnowflakeOrNull
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.coroutineScope
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

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
                    queuePrivateMessage(userId, adjustedMessage)
                }
            }

            rawName.asSnowflakeOrNull() != null -> {
                queuePrivateMessage(rawName, messageWithId)
            }

            else -> {
                sendGameMessage("Unable to resolve user ${recipient.raw}")
            }
        }
    }

    override fun sendGameMessage(message: DiscordMessage) {
        gameMessageChannel.sendMessage(insertGameId(message, contextGameId)).queue()
    }

    override fun sendGameMessage(message: String) {
        gameMessageChannel.sendMessage("Game id: ${contextGameId}\n" + message).queue()
    }

    override suspend fun enqueueEditGameMessage(targetMessage: DiscordMessage, newContentBlock: () -> DiscordMessage?) {
        editChannel.send(MessageEditQueueEntry(targetMessage) {
            newContentBlock()?.let { insertGameId(it, gameId = contextGameId) }
        })
    }
}

private object NullMessageContext : SecretHitlerMessageContext {
    override fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: String,
    ) {
        // Intentionally do nothing.
    }

    override fun sendPrivateMessage(
        recipient: SecretHitlerPlayerExternalName,
        gameId: SecretHitlerGameId,
        message: DiscordMessage,
    ) {
        // Intentionally do nothing.
    }

    override fun sendGameMessage(message: String) {
        // Intentionally do nothing.
    }

    override fun sendGameMessage(message: DiscordMessage) {
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

private object SecretHitlerRepositoryCacheKey
private object SecretHitlerImpersonationMapCacheKey
private object SecretHitlerEditQueueChannelCacheKey

private fun secretHitlerFeature(config: SecretHitlerFeatureConfig) = object : AbstractFeature() {
    private val FeatureContext.impersonationMap
        get() = if (config.enableImpersonation)
            cache(SecretHitlerImpersonationMapCacheKey) {
                SecretHitlerJsonImpersonationMap(config.baseStoragePath.resolve("impersonation_data"),
                    configPersistService)
            }
        else
            null

    private val nameContext = NameContextImpl

    private val FeatureContext.repository
        get() = cache(SecretHitlerRepositoryCacheKey) {
            SecretHitlerRepository(
                gameList = alwaysCloseObject(
                    {
                        JsonSecretHitlerGameList(
                            storagePath = config.baseStoragePath.resolve("games"),
                            persistService = configPersistService,
                        )
                    },
                    {
                        it.close()
                    },
                ),
                channelGameMap = alwaysCloseObject(
                    {
                        JsonSecretHitlerChannelGameMap(
                            storagePath = config.baseStoragePath.resolve("games_by_channel"),
                            persistService = configPersistService,
                        )
                    },
                    {
                        it.close()
                    },
                ),
            )
        }

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
        val gameChannel = gameMessageChannelId?.let { jda.getTextChannelById(it) }

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
        val repository = context.repository
        val impersonationMap = context.impersonationMap

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
        val repository = context.repository
        val impersonationMap = context.impersonationMap
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

        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<SecretHitlerJoinGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleJoin(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerLeaveGameButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleLeave(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorCandidateSelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleChancellorSelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerVoteButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleVote(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPresidentPolicyChoiceButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentPolicySelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorPolicyChoiceButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleChancellorPolicySelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPendingInvestigatePartySelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentInvestigatePowerSelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPendingSpecialElectionSelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentSpecialElectionPowerSelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPendingExecutionSelectionButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentExecutePowerSelection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerChancellorRequestVetoButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handleChancellorVetoRequest(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPresidentAcceptVetoButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentVetoApproval(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }

                withType<SecretHitlerPresidentRejectVetoButtonDescriptor> { context, request ->
                    SecretHitlerButtons.handlePresidentVetoRejection(
                        repository = repository,
                        context = interactionContextFor(context, request.gameId),
                        event = context.event,
                        request = request,
                    )
                }
            },
        )
    }
}

private data class SecretHitlerFeatureConfig(
    val baseStoragePath: Path,
    val enableImpersonation: Boolean,
)

@Serializable
private data class SecretHitlerConfigDto(
    @SerialName("enable_impersonation") val enableImpersonation: Boolean = false,
)

@FeatureSourceFactory
fun secretHitlerFactory() = object : FeatureSource {
    override val featureName: String
        get() = "secret_hitler"

    override fun readConfig(context: FeatureSetupContext): SecretHitlerFeatureConfig {
        val fileConfig =
            readConfigFromFile(context.paths.featureConfigDir.resolve("secret_hitler.json"), SecretHitlerConfigDto()) {
                Json.decodeFromString<SecretHitlerConfigDto>(it)
            }

        return SecretHitlerFeatureConfig(
            baseStoragePath = context.paths.storagePath.resolve("secret_hitler"),
            enableImpersonation = fileConfig.enableImpersonation,
        )
    }

    override fun createFeature(config: Any?): Feature {
        config as SecretHitlerFeatureConfig

        Files.createDirectories(config.baseStoragePath)
        return secretHitlerFeature(config)
    }
}
