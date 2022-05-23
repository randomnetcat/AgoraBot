package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.storage.api.updateRunningGameWithValidation
import org.randomcat.agorabot.util.handleTextResponse
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerUpdateValidationResult.Invalid as InvalidResult
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerUpdateValidationResult.Valid as ValidResult

private sealed class ChancellorSelectResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
        val chancellorName: SecretHitlerPlayerExternalName,
    ) : ChancellorSelectResult()

    sealed class Failure : ChancellorSelectResult()
    object NoSuchGame : Failure()
    object NotPlayer : Failure()
    object Unauthorized : Failure()
    object InvalidState : Failure()
    object IneligibleChancellor : Failure()
}

private fun doStateUpdate(
    gameList: SecretHitlerGameList,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedChancellor: SecretHitlerPlayerNumber,
): ChancellorSelectResult {
    return gameList.updateRunningGameWithValidation(
        id = gameId,
        onNoSuchGame = {
            ChancellorSelectResult.NoSuchGame
        },
        onInvalidType = {
            ChancellorSelectResult.InvalidState
        },
        checkCustomError = { currentState ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)
            val expectedPresidentNumber = currentState.ephemeralState.presidentCandidate

            val chancellorSelectionIsValid = currentState.chancellorSelectionIsValid(
                presidentCandidate = expectedPresidentNumber,
                chancellorCandidate = selectedChancellor,
            )

            when {
                actualPresidentNumber == null -> {
                    InvalidResult(ChancellorSelectResult.NotPlayer)
                }

                actualPresidentNumber != expectedPresidentNumber -> {
                    InvalidResult(ChancellorSelectResult.Unauthorized)
                }

                !chancellorSelectionIsValid -> {
                    InvalidResult(ChancellorSelectResult.IneligibleChancellor)
                }

                else -> {
                    ValidResult(Unit)
                }
            }
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>, _ ->
            val newState = currentState.withEphemeral(
                currentState.ephemeralState.withChancellorSelected(selectedChancellor),
            )

            newState to ChancellorSelectResult.Success(
                newState = newState,
                chancellorName = currentState.globalState.playerMap.playerByNumberKnown(selectedChancellor),
            )
        },
    )
}

internal suspend fun doHandleSecretHitlerChancellorSelect(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
) {
    val gameId = request.gameId
    val actualPresidentName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        val result = doStateUpdate(
            gameList = repository.gameList,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
            selectedChancellor = request.selectedChancellor,
        )

        when (result) {
            is ChancellorSelectResult.Success -> {
                sendSecretHitlerVotingMessage(
                    context = context,
                    gameId = gameId,
                    gameState = result.newState,
                )

                "You selected ${context.renderExternalName(result.chancellorName)}. Voting will now commence."
            }

            is ChancellorSelectResult.NoSuchGame -> {
                "That game no longer exists."
            }

            is ChancellorSelectResult.NotPlayer -> {
                "You are not a player in that game."
            }

            is ChancellorSelectResult.InvalidState -> {
                "You can no longer select a Chancellor in that game."
            }

            is ChancellorSelectResult.Unauthorized -> {
                "You are not the Presidential candidate."
            }

            is ChancellorSelectResult.IneligibleChancellor -> {
                "That person is not eligible to be chancellor."
            }
        }
    }
}
