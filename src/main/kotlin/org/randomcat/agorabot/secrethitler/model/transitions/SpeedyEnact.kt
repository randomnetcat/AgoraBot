package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPolicyType

sealed class SecretHitlerSpeedyEnactResult {
    data class GameContinues(val newGlobalState: SecretHitlerGlobalGameState) : SecretHitlerSpeedyEnactResult()
    data class GameEnds(val winResult: SecretHitlerWinResult) : SecretHitlerSpeedyEnactResult()
}

fun SecretHitlerGlobalGameState.afterSpeedyEnacting(policyType: SecretHitlerPolicyType): SecretHitlerSpeedyEnactResult {
    return when (val nestedResult = this.afterEnacting(policyType)) {
        is SecretHitlerEnactmentResult.GameContinues.NoPower -> {
            SecretHitlerSpeedyEnactResult.GameContinues(newGlobalState = nestedResult.newGlobalState)
        }

        // Speedy enactments do not result in fascist powers being activated
        is SecretHitlerEnactmentResult.GameContinues.PowerActivated -> {
            SecretHitlerSpeedyEnactResult.GameContinues(newGlobalState = nestedResult.newGlobalState)
        }

        is SecretHitlerEnactmentResult.GameEnds -> {
            SecretHitlerSpeedyEnactResult.GameEnds(winResult = nestedResult.winResult)
        }
    }
}
