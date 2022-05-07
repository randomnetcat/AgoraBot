package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerJoinGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerLeaveGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerCommandContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerGameContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.context.SecretHitlerNameContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.handleTextResponse
import java.time.Duration

private sealed class JoinLeaveMapResult {
    data class Failed(val message: String) : JoinLeaveMapResult()
    data class Succeeded(val newState: SecretHitlerGameState.Joining) : JoinLeaveMapResult()
}

private sealed class HandleJoinLeaveInternalState {
    data class Failed(
        val message: String,
    ) : HandleJoinLeaveInternalState()

    data class Succeeded(
        val newState: SecretHitlerGameState.Joining,
    ) : HandleJoinLeaveInternalState()
}

private suspend fun handleJoinLeave(
    context: SecretHitlerGameContext,
    repository: SecretHitlerRepository,
    action: String,
    gameId: SecretHitlerGameId,
    event: ButtonInteractionEvent,
    mapState: (SecretHitlerGameState.Joining) -> JoinLeaveMapResult,
): String {
    return repository.gameList.updateGameTypedWithValidExtract(
        id = gameId,
        onNoSuchGame = {
            "That game does not exist."
        },
        onInvalidType = {
            "That game can no longer be $action."
        },
        validMapper = { oldState: SecretHitlerGameState.Joining ->
            when (val mapResult = mapState(oldState)) {
                is JoinLeaveMapResult.Succeeded -> {
                    val newState = mapResult.newState

                    newState to HandleJoinLeaveInternalState.Succeeded(
                        newState = newState,
                    )
                }

                is JoinLeaveMapResult.Failed -> {
                    oldState to HandleJoinLeaveInternalState.Failed(mapResult.message)
                }
            }
        },
        afterValid = { state ->
            when (state) {
                is HandleJoinLeaveInternalState.Succeeded -> {
                    context.enqueueEditGameMessage(
                        targetMessage = checkNotNull(event.message),
                        newContentBlock = {
                            // Don't update if the state has changed out from under us.
                            val currentState = repository.gameList.gameById(gameId)

                            if (currentState == state.newState) {
                                MessageBuilder(event.message)
                                    .setEmbeds(
                                        formatSecretHitlerJoinMessageEmbed(
                                            context = context,
                                            state = state.newState,
                                        ),
                                    )
                                    .build()
                            } else {
                                null
                            }
                        }
                    )

                    "Successfully $action."
                }

                is HandleJoinLeaveInternalState.Failed -> {
                    state.message
                }
            }
        },
    )
}

internal suspend fun doHandleSecretHitlerJoin(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerJoinGameButtonDescriptor,
) {
    val playerName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        handleJoinLeave(
            context = context,
            repository = repository,
            action = "joined",
            gameId = request.gameId,
            event = event,
        ) { gameState ->
            when (val result = gameState.tryWithNewPlayer(playerName)) {
                is SecretHitlerGameState.Joining.TryJoinResult.Success -> {
                    JoinLeaveMapResult.Succeeded(result.newState)
                }

                is SecretHitlerGameState.Joining.TryJoinResult.Full -> {
                    JoinLeaveMapResult.Failed("That game is full.")
                }

                is SecretHitlerGameState.Joining.TryJoinResult.AlreadyJoined -> {
                    JoinLeaveMapResult.Failed("You are already a player in that game.")
                }
            }
        }
    }
}

internal suspend fun doHandleSecretHitlerLeave(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerLeaveGameButtonDescriptor,
) {
    val playerName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        handleJoinLeave(
            context = context,
            repository = repository,
            action = "left",
            gameId = request.gameId,
            event = event,
        ) { gameState ->
            when (val result = gameState.tryWithoutPlayer(playerName)) {
                is SecretHitlerGameState.Joining.TryLeaveResult.Success -> {
                    JoinLeaveMapResult.Succeeded(result.newState)
                }

                is SecretHitlerGameState.Joining.TryLeaveResult.NotPlayer -> {
                    JoinLeaveMapResult.Failed("You are not a player in that game.")
                }
            }
        }
    }
}

private val JOIN_LEAVE_DURATION = Duration.ofDays(1)

private fun formatSecretHitlerJoinMessageEmbed(
    context: SecretHitlerNameContext,
    state: SecretHitlerGameState.Joining,
): MessageEmbed {
    return EmbedBuilder()
        .setTitle("Secret Hitler Game")
        .addField(
            "Players",
            state
                .playerNames
                .joinToString("\n") { name -> context.renderExternalName(name) }
                .ifEmpty { "[None yet]" },
            false,
        )
        .build()
}

private fun formatSecretHitlerJoinMessage(
    context: SecretHitlerNameContext,
    state: SecretHitlerGameState.Joining,
    joinButtonId: String,
    leaveButtonId: String,
): DiscordMessage {
    return MessageBuilder(formatSecretHitlerJoinMessageEmbed(context = context, state = state))
        .setActionRows(
            ActionRow.of(
                Button.success(joinButtonId, "Join"),
                Button.danger(leaveButtonId, "Leave"),
            ),
        )
        .build()
}

internal suspend fun doSendSecretHitlerJoinLeaveMessage(
    context: SecretHitlerCommandContext,
    gameId: SecretHitlerGameId,
    state: SecretHitlerGameState.Joining,
) {
    context.sendGameMessage(
        formatSecretHitlerJoinMessage(
            context = context,
            state = state,
            joinButtonId = context.newButtonId(
                descriptor = SecretHitlerJoinGameButtonDescriptor(gameId),
                expiryDuration = JOIN_LEAVE_DURATION,
            ),
            leaveButtonId = context.newButtonId(
                descriptor = SecretHitlerLeaveGameButtonDescriptor(gameId),
                expiryDuration = JOIN_LEAVE_DURATION,
            ),
        ),
    )
}
