package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.secrethitler.model.playerIsHitler

sealed class SecretHitlerExecutionResult {
    sealed class GameEnds : SecretHitlerExecutionResult() {
        abstract val winResult: SecretHitlerWinResult

        object HitlerKilled : GameEnds() {
            override val winResult: SecretHitlerWinResult
                get() = SecretHitlerWinResult.LiberalsWin
        }
    }

    data class GameContinues(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
    ) : SecretHitlerExecutionResult()
}

fun SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending.Execution>.afterExecuting(
    executedPlayerNumber: SecretHitlerPlayerNumber,
): SecretHitlerExecutionResult {
    require(executedPlayerNumber != ephemeralState.presidentNumber)

    if (globalState.roleMap.playerIsHitler(executedPlayerNumber)) {
        return SecretHitlerExecutionResult.GameEnds.HitlerKilled
    }

    return SecretHitlerExecutionResult.GameContinues(
        newState = globalState
            .copy(
                playerMap = globalState.playerMap.withoutPlayer(executedPlayerNumber),
                roleMap = globalState.roleMap.withoutPlayer(executedPlayerNumber),
            )
            .stateForElectionAfterAdvancingTicker(),
    )
}
