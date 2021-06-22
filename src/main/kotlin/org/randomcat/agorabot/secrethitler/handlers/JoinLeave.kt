package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerJoinGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerLeaveGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract
import java.math.BigInteger

private fun handleTextResponse(event: ButtonClickEvent, responseBlock: () -> String) {
    event.deferReply(true).queue { hook ->
        hook.sendMessage(responseBlock()).queue()
    }
}

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
        val updateNumber: BigInteger,
    ) : HandleJoinLeaveInternalState()
}

private fun handleJoinLeave(
    repository: SecretHitlerRepository,
    action: String,
    gameId: SecretHitlerGameId,
    event: ButtonClickEvent,
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
                        updateNumber = SecretHitlerJoinLeaveMessageQueue.nextUpdateNumber(),
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
                    SecretHitlerJoinLeaveMessageQueue.sendUpdateAction(
                        SecretHitlerJoinLeaveMessageQueue.UpdateAction.JoinMessageUpdate(
                            updateNumber = state.updateNumber,
                            message = checkNotNull(event.message),
                            state = state.newState,
                        ),
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

internal fun doHandleSecretHitlerJoin(
    repository: SecretHitlerRepository,
    context: SecretHitlerNameContext,
    event: ButtonClickEvent,
    request: SecretHitlerJoinGameButtonDescriptor,
) {
    val playerName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        handleJoinLeave(
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

internal fun doHandleSecretHitlerLeave(
    repository: SecretHitlerRepository,
    context: SecretHitlerNameContext,
    event: ButtonClickEvent,
    request: SecretHitlerLeaveGameButtonDescriptor,
) {
    val playerName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        handleJoinLeave(
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
