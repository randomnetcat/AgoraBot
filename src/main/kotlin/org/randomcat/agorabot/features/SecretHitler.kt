package org.randomcat.agorabot.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.formatSecretHitlerJoinMessageEmbed
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.updateGameTyped
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

private val updateNumber = AtomicReference<BigInteger>(BigInteger.ZERO)

private fun nextUpdateNumber(): BigInteger {
    return updateNumber.getAndUpdate { it + BigInteger.ONE }
}

private sealed class UpdateAction {
    abstract val updateNumber: BigInteger

    data class JoinMessageUpdate(
        override val updateNumber: BigInteger,
        val message: Message,
        val state: SecretHitlerGameState.Joining,
    ) : UpdateAction()
}

private val logger = LoggerFactory.getLogger("SecretHitlerFeature")

private suspend fun doHandleMessageUpdates(channel: Channel<UpdateAction>) {
    var largestUpdateNumber: BigInteger? = null

    val futureMap = mutableMapOf<String /* ChannelId */, CompletableFuture<Unit>>()

    var pruneCount = 0

    for (updateAction in channel) {
        // Every 100 actions, remove the entries with futures that are completed. This means that any new updates
        // would execute immediately anyway, so the future is unnecessary.
        // 100 chosen arbitrarily.
        if (pruneCount == 100) {
            pruneCount = 0

            futureMap.entries.removeAll {
                it.value.isDone
            }
        }

        if (largestUpdateNumber == null || updateAction.updateNumber > largestUpdateNumber) {
            try {
                largestUpdateNumber = updateAction.updateNumber

                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (updateAction) {
                    is UpdateAction.JoinMessageUpdate -> {
                        val newMessage =
                            MessageBuilder(updateAction.message)
                                .setEmbed(formatSecretHitlerJoinMessageEmbed(updateAction.state))
                                .build()

                        val restAction = updateAction.message.editMessage(newMessage).map { Unit }

                        // Ensure that the message edit action is queued only after any previous update in that channel
                        // has completed.
                        futureMap.compute(updateAction.message.channel.id) { _, old ->
                            if (old != null) {
                                old.thenCompose { _ ->
                                    restAction.submit()
                                }
                            } else {
                                restAction.submit()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error ignored while attempting to handle update task $updateAction", e)
            }
        }
    }
}

private fun setupMessageUpdateChannel(): SendChannel<UpdateAction> {
    val channel = Channel<UpdateAction>(capacity = 100)

    CoroutineScope(Dispatchers.Default).launch {
        doHandleMessageUpdates(channel)
    }

    return channel
}

private val messageUpdateChannel: SendChannel<UpdateAction> by lazy {
    setupMessageUpdateChannel()
}

private fun sendUpdateAction(action: UpdateAction) {
    messageUpdateChannel.trySendBlocking(action)
}

private fun handleTextResponse(event: ButtonClickEvent, responseBlock: () -> String) {
    event.deferReply(true).queue() { hook ->
        hook.sendMessage(responseBlock()).queue()
    }
}

private fun handleJoinLeave(
    repository: SecretHitlerRepository,
    action: String,
    gameId: SecretHitlerGameId,
    event: ButtonClickEvent,
    mapState: (SecretHitlerGameState.Joining) -> SecretHitlerGameState.Joining,
): String {
    lateinit var updateNumber: BigInteger
    lateinit var newState: SecretHitlerGameState.Joining

    return repository.gameList.updateGameTyped(
        id = gameId,
        onNoSuchGame = {
            "That game does not exist."
        },
        onInvalidType = {
            "That game can no longer be $action."
        },
        validMapper = { gameState: SecretHitlerGameState.Joining ->
            updateNumber = nextUpdateNumber()

            mapState(gameState).also {
                newState = it
            }
        },
        afterValid = {
            sendUpdateAction(
                UpdateAction.JoinMessageUpdate(
                    updateNumber = updateNumber,
                    message = checkNotNull(event.message),
                    state = newState,
                ),
            )

            "Successfully $action."
        },
    )
}

fun secretHitlerFeature(repository: SecretHitlerRepository) = object : Feature {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "secret_hitler" to SecretHitlerCommand(context.defaultCommandStrategy, repository = repository),
        )
    }

    override fun buttonData(): FeatureButtonData {
        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<SecretHitlerCommand.JoinGameRequestDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        handleJoinLeave(
                            repository = repository,
                            action = "joined",
                            gameId = request.gameId,
                            event = context.event,
                        ) { gameState ->
                            gameState.withNewPlayer(SecretHitlerPlayerExternalName(context.event.user.id))
                        }
                    }
                }

                withType<SecretHitlerCommand.LeaveGameRequestDescriptor> { context, request ->
                    handleTextResponse(context.event) {
                        handleJoinLeave(
                            repository = repository,
                            action = "left",
                            gameId = request.gameId,
                            event = context.event,
                        ) { gameState ->
                            gameState.withoutPlayer(SecretHitlerPlayerExternalName(context.event.user.id))
                        }
                    }
                }
            },
        )
    }
}
