package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.model.transitions.afterPresidentPolicySelected
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidation
import org.randomcat.agorabot.util.handleTextResponse
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Invalid as InvalidResult
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Valid as ValidResult

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
    selectedPolicyIndex: Int,
): PresidentPolicySelectedResult {
    return repository.gameList.updateRunningGameWithValidation(
        gameId,
        onNoSuchGame = {
            PresidentPolicySelectedResult.NoSuchGame
        },
        onInvalidType = {
            PresidentPolicySelectedResult.InvalidState
        },
        checkCustomError = { currentState ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)
            val expectedPresidentNumber = currentState.ephemeralState.governmentMembers.president

            when {
                actualPresidentNumber == null -> {
                    InvalidResult(PresidentPolicySelectedResult.NotPlayer)
                }

                actualPresidentNumber != expectedPresidentNumber -> {
                    InvalidResult(PresidentPolicySelectedResult.Unauthorized)
                }

                else -> {
                    ValidResult(Unit)
                }
            }
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>, _ ->
            val newState = currentState.afterPresidentPolicySelected(policyIndex = selectedPolicyIndex)

            newState to PresidentPolicySelectedResult.Success(newState = newState)
        },
    )
}

internal suspend fun doHandleSecretHitlerPresidentPolicySelected(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerPresidentPolicyChoiceButtonDescriptor,
) {
    handleTextResponse(event) {
        val updateResult = doStateUpdate(
            repository = repository,
            gameId = request.gameId,
            actualPresidentName = context.nameFromInteraction(event.interaction),
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
