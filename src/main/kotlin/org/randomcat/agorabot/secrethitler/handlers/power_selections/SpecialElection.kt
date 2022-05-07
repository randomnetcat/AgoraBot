package org.randomcat.agorabot.secrethitler.handlers.power_selections

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingSpecialElectionSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.secretHitlerSendChancellorSelectionMessage
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.stateForNewElectionWith
import org.randomcat.agorabot.util.handleTextResponse

private sealed class SpecialElectionSelectionResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
        val selectedPlayerName: SecretHitlerPlayerExternalName,
    ) : SpecialElectionSelectionResult()

    data class Failure(val failureReason: SecretHitlerPowerCommonFailure) : SpecialElectionSelectionResult()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
): SpecialElectionSelectionResult {
    return repository.gameList.updateGameForPowerSelection(
        gameId = gameId,
        actualPresidentName = actualPresidentName,
        selectedPlayerNumber = selectedPlayerNumber,
        mapError = SpecialElectionSelectionResult::Failure,
        onValid = { checkResult, currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending.SpecialElection> ->
            // All players are eligible (except the current President, but updateGameForPowerSelection will check that).
            // The selected President must choose an *eligible* Chancellor, so no additional state is needed.
            val newState = currentState.globalState.stateForNewElectionWith(selectedPlayerNumber)

            newState to SpecialElectionSelectionResult.Success(
                newState = newState,
                selectedPlayerName = checkResult.selectedPlayerName,
            )
        },
    )
}

suspend fun doHandleSecretHitlerPresidentSpecialElectionPowerSelection(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerPendingSpecialElectionSelectionButtonDescriptor,
) {
    handleTextResponse(event) {
        val actualPresidentName = context.nameFromInteraction(event.interaction)
        val gameId = request.gameId

        val updateResult = doStateUpdate(
            repository = repository,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
            selectedPlayerNumber = request.selectedPlayer,
        )

        when (updateResult) {
            is SpecialElectionSelectionResult.Success -> {
                sendSecretHitlerCommonPowerSelectionNotification(
                    context = context,
                    title = "Special Election Selection",
                    description = "The President has selected the Presidential candidate for the Special Election.",
                    presidentName = actualPresidentName,
                    selectedPlayerName = updateResult.selectedPlayerName,
                )

                secretHitlerSendChancellorSelectionMessage(
                    context = context,
                    gameId = gameId,
                    state = updateResult.newState,
                )

                "A Special Election will be initiated with that player as the Presidential candidate."
            }

            is SpecialElectionSelectionResult.Failure -> {
                updateResult.failureReason.standardErrorMessage
            }
        }
    }
}
