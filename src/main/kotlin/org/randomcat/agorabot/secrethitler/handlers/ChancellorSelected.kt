package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse

private sealed class ChancellorSelectResult {
    data class Success(
        val newState: SecretHitlerGameState.Running,
        val chancellorName: SecretHitlerPlayerExternalName,
    ) : ChancellorSelectResult()

    sealed class Failure : ChancellorSelectResult()
    object Unauthorized : Failure()
    object InvalidState : Failure()
    object IneligibleChancellor : Failure()
}

private const val INVALID_STATE_MESSAGE = "You can no longer select a Chancellor in that game."

internal fun doHandleSecretHitlerChancellorSelect(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
) {
    val expectedPresidentNumber = request.president
    val actualPresidentName = context.nameFromInteraction(event.interaction)

    handleTextResponse(event) {
        repository.gameList.updateGameTypedWithValidExtract(
            id = request.gameId,
            onNoSuchGame = {
                "That game no longer exists."
            },
            onInvalidType = {
                INVALID_STATE_MESSAGE
            },
            validMapper = { currentState: SecretHitlerGameState.Running ->
                val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)

                if (actualPresidentNumber != expectedPresidentNumber) {
                    return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.Unauthorized
                }

                if (currentState.ephemeralState !is SecretHitlerEphemeralState.ChancellorSelectionPending) {
                    return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.InvalidState
                }

                check(currentState.ephemeralState.presidentCandidate == actualPresidentNumber)

                val selectedChancellorNumber = request.selectedChancellor

                currentState.chancellorSelectionIsValid(
                    presidentCandidate = actualPresidentNumber,
                    chancellorCandidate = selectedChancellorNumber,
                ).let { isValid ->
                    if (!isValid) {
                        return@updateGameTypedWithValidExtract currentState to ChancellorSelectResult.IneligibleChancellor
                    }
                }

                val newState = currentState.copy(
                    ephemeralState = currentState.ephemeralState.selectChancellor(selectedChancellorNumber),
                )

                newState to ChancellorSelectResult.Success(
                    newState = newState,
                    chancellorName = currentState.globalState.playerMap.playerByNumber(selectedChancellorNumber),
                )
            },
            afterValid = { result ->
                when (result) {
                    is ChancellorSelectResult.Success -> {
                        "You selected <@${result.chancellorName.raw}>. Voting will now commence."
                    }

                    is ChancellorSelectResult.InvalidState -> {
                        INVALID_STATE_MESSAGE
                    }

                    is ChancellorSelectResult.Unauthorized -> {
                        "You are not the Presidential candidate."
                    }

                    is ChancellorSelectResult.IneligibleChancellor -> {
                        "That person is not eligible to be chancellor."
                    }
                }
            }
        )
    }
}
