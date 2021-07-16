package org.randomcat.agorabot.secrethitler.handlers.power_selections

import org.randomcat.agorabot.secrethitler.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidExtract

sealed class SecretHitlerPowerCommonCheckResult

data class SecretHitlerPowerCommonSuccess(
    val actualPresidentNumber: SecretHitlerPlayerNumber,
    val selectedPlayerName: SecretHitlerPlayerExternalName,
) : SecretHitlerPowerCommonCheckResult()

sealed class SecretHitlerPowerCommonFailure(val standardErrorMessage: String) : SecretHitlerPowerCommonCheckResult() {
    object Unauthorized :
        SecretHitlerPowerCommonFailure("You are not the President in that game.")

    object InvalidState :
        SecretHitlerPowerCommonFailure("You can no longer select a player to investigate in that game.")

    object NoSuchGame :
        SecretHitlerPowerCommonFailure("That game no longer exists.")

    object ActorNotPlayer :
        SecretHitlerPowerCommonFailure("You are not a player in that game.")

    object SelectedNotPlayer :
        SecretHitlerPowerCommonFailure("The person you have selected is no longer a player in that game.")
}

fun verifySecretHitlerPowerUse(
    playerMap: SecretHitlerPlayerMap,
    actualPresidentName: SecretHitlerPlayerExternalName,
    expectedPresidentNumber: SecretHitlerPlayerNumber,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
): SecretHitlerPowerCommonCheckResult {
    val actualPresidentNumber = playerMap.numberByPlayer(actualPresidentName)

    if (actualPresidentNumber == null) {
        return SecretHitlerPowerCommonFailure.ActorNotPlayer
    }

    if (actualPresidentNumber != expectedPresidentNumber) {
        return SecretHitlerPowerCommonFailure.Unauthorized
    }

    val selectedPlayerName = playerMap.playerByNumber(selectedPlayerNumber)

    if (selectedPlayerName == null) {
        return SecretHitlerPowerCommonFailure.SelectedNotPlayer
    }

    return SecretHitlerPowerCommonSuccess(
        actualPresidentNumber = actualPresidentNumber,
        selectedPlayerName = selectedPlayerName,
    )
}

internal inline fun <reified E, R> SecretHitlerGameList.updateGameForPowerSelection(
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    selectedPlayerNumber: SecretHitlerPlayerNumber,
    crossinline mapError: (SecretHitlerPowerCommonFailure) -> R,
    crossinline onValid: (commonResult: SecretHitlerPowerCommonSuccess, currentState: SecretHitlerGameState.Running.With<E>) -> Pair<SecretHitlerGameState, R>,
): R where E : SecretHitlerEphemeralState.PolicyPending {
    return updateRunningGameWithValidExtract(
        id = gameId,
        onNoSuchGame = { mapError(SecretHitlerPowerCommonFailure.NoSuchGame) },
        onInvalidType = { mapError(SecretHitlerPowerCommonFailure.InvalidState) },
        validMapper = { currentState: SecretHitlerGameState.Running.With<E> ->
            val verifyResult = verifySecretHitlerPowerUse(
                playerMap = currentState.globalState.playerMap,
                actualPresidentName = actualPresidentName,
                expectedPresidentNumber = currentState.ephemeralState.presidentNumber,
                selectedPlayerNumber = selectedPlayerNumber,
            )

            when (verifyResult) {
                is SecretHitlerPowerCommonSuccess -> {
                    onValid(verifyResult, currentState)
                }

                is SecretHitlerPowerCommonFailure -> {
                    currentState to mapError(verifyResult)
                }
            }
        },
        afterValid = { result ->
            result
        },
    )
}
