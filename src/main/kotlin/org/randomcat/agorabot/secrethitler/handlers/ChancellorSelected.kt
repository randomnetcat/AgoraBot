package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse

private sealed class ChancellorSelectResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
        val chancellorName: SecretHitlerPlayerExternalName,
    ) : ChancellorSelectResult()

    sealed class Failure : ChancellorSelectResult()
    object NoSuchGame : Failure()
    object Unauthorized : Failure()
    object InvalidState : Failure()
    object IneligibleChancellor : Failure()
}

private fun doStateUpdate(
    gameList: SecretHitlerGameList,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    expectedPresidentNumber: SecretHitlerPlayerNumber,
    selectedChancellor: SecretHitlerPlayerNumber,
): ChancellorSelectResult {
    return gameList.updateGameTypedWithValidExtract(
        id = gameId,
        onNoSuchGame = {
            ChancellorSelectResult.NoSuchGame
        },
        onInvalidType = {
            ChancellorSelectResult.InvalidState
        },
        validMapper = { currentState: SecretHitlerGameState.Running ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)

            if (actualPresidentNumber != expectedPresidentNumber) {
                return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.Unauthorized
            }

            val typedState = currentState.tryWith<SecretHitlerEphemeralState.ChancellorSelectionPending>()

            if (typedState == null) {
                return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.InvalidState
            }

            check(typedState.ephemeralState.presidentCandidate == actualPresidentNumber)

            typedState.chancellorSelectionIsValid(
                presidentCandidate = actualPresidentNumber,
                chancellorCandidate = selectedChancellor,
            ).let { isValid ->
                if (!isValid) {
                    return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.IneligibleChancellor
                }
            }

            val newState = typedState.withEphemeral(
                typedState.ephemeralState.selectChancellor(selectedChancellor),
            )

            newState to ChancellorSelectResult.Success(
                newState = newState,
                chancellorName = currentState.globalState.playerMap.playerByNumberKnown(selectedChancellor),
            )
        },
        afterValid = { result ->
            result
        },
    )
}

internal fun doHandleSecretHitlerChancellorSelect(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
) {
    val gameId = request.gameId
    val actualPresidentName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        val result = doStateUpdate(
            gameList = repository.gameList,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
            expectedPresidentNumber = request.president,
            selectedChancellor = request.selectedChancellor,
        )

        when (result) {
            is ChancellorSelectResult.Success -> {
                doSendSecretHitlerVotingMessage(
                    context = context,
                    gameId = gameId,
                    gameState = result.newState,
                )

                "You selected ${context.renderExternalName(result.chancellorName)}. Voting will now commence."
            }

            is ChancellorSelectResult.NoSuchGame -> {
                "That game no longer exists."
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
