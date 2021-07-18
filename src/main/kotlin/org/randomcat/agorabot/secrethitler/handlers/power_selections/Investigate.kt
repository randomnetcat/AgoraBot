package org.randomcat.agorabot.secrethitler.handlers.power_selections

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingInvestigatePartySelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.secretHitlerSendChancellorSelectionMessage
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.stateForElectionAfterAdvancingTicker
import org.randomcat.agorabot.util.handleTextResponse

private sealed class InvestigateSelectionResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
        val selectedPlayerName: SecretHitlerPlayerExternalName,
        val selectedPlayerParty: SecretHitlerParty,
    ) : InvestigateSelectionResult()

    sealed class Failure : InvestigateSelectionResult() {
        data class Common(val failureReason: SecretHitlerPowerCommonFailure) : Failure()
        object AlreadyInvestigated : Failure()
    }
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
): InvestigateSelectionResult {
    return repository.gameList.updateGameForPowerSelection(
        gameId = gameId,
        actualPresidentName = actualPresidentName,
        selectedPlayerNumber = selectedPlayerNumber,
        mapError = InvestigateSelectionResult.Failure::Common,
        onValid = { commonResult, currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending.InvestigateParty> ->
            if (currentState.globalState.powersState.previouslyInvestigatedPlayers.contains(selectedPlayerNumber)) {
                return@updateGameForPowerSelection currentState to InvestigateSelectionResult.Failure.AlreadyInvestigated
            }

            val stateAfterHistoryUpdate = currentState.globalState.copy(
                powersState = currentState.globalState.powersState.afterInvestigationOf(selectedPlayerNumber),
            )

            val finalState = stateAfterHistoryUpdate.stateForElectionAfterAdvancingTicker()

            finalState to InvestigateSelectionResult.Success(
                newState = finalState,
                selectedPlayerName = commonResult.selectedPlayerName,
                selectedPlayerParty = currentState.globalState.roleMap.roleOf(selectedPlayerNumber).party,
            )
        },
    )
}

private val SecretHitlerParty.readableName: String
    get() = when (this) {
        SecretHitlerParty.LIBERAL -> "Liberal"
        SecretHitlerParty.FASCIST -> "Fascist"
    }

private fun sendInvestigationResultMessages(
    context: SecretHitlerInteractionContext,
    gameId: SecretHitlerGameId,
    presidentName: SecretHitlerPlayerExternalName,
    selectedPlayerName: SecretHitlerPlayerExternalName,
    investigationResult: SecretHitlerParty,
) {
    sendSecretHitlerCommonPowerSelectionNotification(
        context = context,
        title = "Investigate Party Selection",
        description = "The President has been informed of the selected player's party.",
        presidentName = presidentName,
        selectedPlayerName = selectedPlayerName,
    )

    context.sendPrivateMessage(
        gameId = gameId,
        recipient = presidentName,
        message = MessageBuilder(
            EmbedBuilder()
                .setTitle("Party Investigation Result")
                .setDescription("${context.renderExternalName(selectedPlayerName)}'s party is ${investigationResult.readableName}")
                .build()
        ).build(),
    )
}

fun doHandleSecretHitlerPresidentInvestigatePowerSelection(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerPendingInvestigatePartySelectionButtonDescriptor,
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
            is InvestigateSelectionResult.Success -> {
                sendInvestigationResultMessages(
                    context = context,
                    gameId = gameId,
                    presidentName = actualPresidentName,
                    selectedPlayerName = updateResult.selectedPlayerName,
                    investigationResult = updateResult.selectedPlayerParty,
                )

                secretHitlerSendChancellorSelectionMessage(
                    context = context,
                    gameId = gameId,
                    state = updateResult.newState,
                )

                "You will be informed of that player's party."
            }

            is InvestigateSelectionResult.Failure.Common -> {
                updateResult.failureReason.standardErrorMessage
            }

            is InvestigateSelectionResult.Failure.AlreadyInvestigated -> {
                "That player has already been investigated in this game."
            }
        }
    }
}
