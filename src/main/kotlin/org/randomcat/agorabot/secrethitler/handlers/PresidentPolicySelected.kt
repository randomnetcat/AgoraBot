package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.afterPresidentPolicySelected
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse

private sealed class PresidentPolicySelectedResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>,
    ) : PresidentPolicySelectedResult()

    sealed class Failure : PresidentPolicySelectedResult()
    object Unauthorized : Failure()
    object InvalidState : Failure()
    object NoSuchGame : Failure()
    object NotPlayer : Failure()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    expectedPresidentNumber: SecretHitlerPlayerNumber,
    selectedPolicyIndex: Int,
): PresidentPolicySelectedResult {
    return repository.gameList.updateRunningGameWithValidExtract(
        gameId,
        onNoSuchGame = {
            PresidentPolicySelectedResult.NoSuchGame
        },
        onInvalidType = {
            PresidentPolicySelectedResult.InvalidState
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending> ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)
            if (actualPresidentNumber == null) {
                return@updateRunningGameWithValidExtract currentState to PresidentPolicySelectedResult.NotPlayer
            }

            if (actualPresidentNumber != expectedPresidentNumber) {
                return@updateRunningGameWithValidExtract currentState to PresidentPolicySelectedResult.Unauthorized
            }

            check(actualPresidentNumber == currentState.ephemeralState.governmentMembers.president)

            val newState = currentState.afterPresidentPolicySelected(policyIndex = selectedPolicyIndex)

            newState to PresidentPolicySelectedResult.Success(newState = newState)
        },
        afterValid = { result ->
            result
        },
    )
}

internal fun doHandleSecretHitlerPresidentPolicySelected(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerPresidentPolicyChoiceButtonDescriptor,
) {
    handleTextResponse(event) {
        val updateResult = doStateUpdate(
            repository = repository,
            gameId = request.gameId,
            actualPresidentName = context.nameFromInteraction(event.interaction),
            expectedPresidentNumber = request.president,
            selectedPolicyIndex = request.policyIndex,
        )

        when (updateResult) {
            is PresidentPolicySelectedResult.Success -> {
                sendSecretHitlerChancellorPolicySelectionMessage(
                    context = context,
                    gameId = request.gameId,
                    state = updateResult.newState,
                )

                "You have selected a policy to discard. The Chancellor will now select a policy to enact."
            }

            is PresidentPolicySelectedResult.Unauthorized -> {
                "You are not the President in that game."
            }

            is PresidentPolicySelectedResult.InvalidState -> {
                "You can no longer select a policy in that game."
            }

            is PresidentPolicySelectedResult.NoSuchGame -> {
                "That game no longer exists."
            }

            is PresidentPolicySelectedResult.NotPlayer -> {
                "You are not a player in that game."
            }
        }
    }
}
