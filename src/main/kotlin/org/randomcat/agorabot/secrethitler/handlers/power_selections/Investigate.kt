package org.randomcat.agorabot.secrethitler.handlers.power_selections

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingInvestigatePartySelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.secretHitlerSendChancellorSelectionMessage
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.afterAdvancingTickerAndNewElection
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse

private sealed class InvestigateSelectionResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
        val presidentName: SecretHitlerPlayerExternalName,
        val selectedPlayerName: SecretHitlerPlayerExternalName,
        val selectedPlayerParty: SecretHitlerParty,
    ) : InvestigateSelectionResult()

    sealed class Failure : InvestigateSelectionResult()
    object Unauthorized : Failure()
    object InvalidState : Failure()
    object NoSuchGame : Failure()
    object ActorNotPlayer : Failure()
    object SelectedNotPlayer : Failure()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
): InvestigateSelectionResult {
    return repository.gameList.updateRunningGameWithValidExtract(
        id = gameId,
        onNoSuchGame = { InvestigateSelectionResult.NoSuchGame },
        onInvalidType = { InvestigateSelectionResult.InvalidState },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending.InvestigateParty> ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)
            if (actualPresidentNumber == null) {
                return@updateRunningGameWithValidExtract currentState to InvestigateSelectionResult.ActorNotPlayer
            }

            val expectedPresidentNumber = currentState.ephemeralState.presidentNumber

            if (actualPresidentNumber != expectedPresidentNumber) {
                return@updateRunningGameWithValidExtract currentState to InvestigateSelectionResult.Unauthorized
            }

            val selectedPlayerName = currentState.globalState.playerMap.playerByNumber(selectedPlayerNumber)
            if (selectedPlayerName == null) {
                return@updateRunningGameWithValidExtract currentState to InvestigateSelectionResult.SelectedNotPlayer
            }

            val newState = currentState.afterAdvancingTickerAndNewElection()

            newState to InvestigateSelectionResult.Success(
                newState = newState,
                presidentName = actualPresidentName,
                selectedPlayerName = selectedPlayerName,
                selectedPlayerParty = currentState.globalState.roleMap.roleOf(selectedPlayerNumber).party,
            )
        },
        afterValid = { result ->
            result
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
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle("Investigate Party Selection")
                .setDescription("The President has been informed of the selected player's party.")
                .addField(
                    "President",
                    context.renderExternalName(presidentName),
                    true,
                )
                .addField(
                    "Investigated Player",
                    context.renderExternalName(selectedPlayerName),
                    true,
                )
                .build(),
        ).build(),
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

        val updateResult = doStateUpdate(
            repository = repository,
            gameId = gameId,
            actualPresidentName = context.nameFromInteraction(event.interaction),
            selectedPlayerNumber = request.selectedPlayer,
        )

        when (updateResult) {
            is InvestigateSelectionResult.Success -> {
                sendInvestigationResultMessages(
                    context = context,
                    gameId = gameId,
                    presidentName = updateResult.presidentName,
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

            is InvestigateSelectionResult.NoSuchGame -> {
                "That game no longer exists."
            }

            is InvestigateSelectionResult.ActorNotPlayer -> {
                "You are not a player in that game."
            }

            is InvestigateSelectionResult.Unauthorized -> {
                "You are not the President in that game."
            }

            is InvestigateSelectionResult.InvalidState -> {
                "You can no longer select a player to investigate in that game."
            }

            is InvestigateSelectionResult.SelectedNotPlayer -> {
                "The person you have selected is no longer a player in that game."
            }
        }
    }
}
