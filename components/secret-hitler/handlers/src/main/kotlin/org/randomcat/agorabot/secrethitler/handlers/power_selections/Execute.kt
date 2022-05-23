package org.randomcat.agorabot.secrethitler.handlers.power_selections

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingExecutionSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.secretHitlerSendChancellorSelectionMessage
import org.randomcat.agorabot.secrethitler.handlers.sendSecretHitlerWinMessage
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerExecutionResult
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerWinResult
import org.randomcat.agorabot.secrethitler.model.transitions.afterExecuting
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import org.randomcat.agorabot.util.handleTextResponse

private sealed class ExecuteSelectResult {
    sealed class Success : ExecuteSelectResult() {
        data class GameContinues(
            val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
            override val selectedPlayerName: SecretHitlerPlayerExternalName,
        ) : Success()

        data class GameEnds(
            val winResult: SecretHitlerWinResult,
            override val selectedPlayerName: SecretHitlerPlayerExternalName,
        ) : Success()

        abstract val selectedPlayerName: SecretHitlerPlayerExternalName
    }

    data class Failure(val failureReason: SecretHitlerPowerCommonFailure) : ExecuteSelectResult()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
): ExecuteSelectResult {
    return repository.gameList.updateGameForPowerSelection(
        gameId = gameId,
        actualPresidentName = actualPresidentName,
        selectedPlayerNumber = selectedPlayerNumber,
        mapError = ExecuteSelectResult::Failure,
        onValid = { commonResult, currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending.Execution> ->
            when (val executionResult = currentState.afterExecuting(selectedPlayerNumber)) {
                is SecretHitlerExecutionResult.GameContinues -> {
                    val newState = executionResult.newState

                    newState to ExecuteSelectResult.Success.GameContinues(
                        newState = newState,
                        selectedPlayerName = commonResult.selectedPlayerName,
                    )
                }

                is SecretHitlerExecutionResult.GameEnds -> {
                    SecretHitlerGameState.Completed to ExecuteSelectResult.Success.GameEnds(
                        winResult = executionResult.winResult,
                        selectedPlayerName = commonResult.selectedPlayerName,
                    )
                }
            }
        },
    )
}

suspend fun doHandleSecretHitlerPresidentExecutePowerSelection(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerPendingExecutionSelectionButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId
        val actualPresidentName = context.nameFromInteraction(event.interaction)

        val updateResult = doStateUpdate(
            repository = repository,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
            selectedPlayerNumber = request.selectedPlayer,
        )

        when (updateResult) {
            is ExecuteSelectResult.Success -> {
                sendSecretHitlerCommonPowerSelectionNotification(
                    context = context,
                    title = "Execution Selection",
                    description = "The President has selected a player to execute.",
                    presidentName = actualPresidentName,
                    selectedPlayerName = updateResult.selectedPlayerName,
                )

                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (updateResult) {
                    is ExecuteSelectResult.Success.GameContinues -> {
                        secretHitlerSendChancellorSelectionMessage(
                            context = context,
                            gameId = gameId,
                            state = updateResult.newState,
                        )
                    }

                    is ExecuteSelectResult.Success.GameEnds -> {
                        sendSecretHitlerWinMessage(
                            context = context,
                            winResult = updateResult.winResult,
                        )
                    }
                }

                "That player has been executed and removed from the game."
            }

            is ExecuteSelectResult.Failure -> {
                updateResult.failureReason.standardErrorMessage
            }
        }
    }
}
