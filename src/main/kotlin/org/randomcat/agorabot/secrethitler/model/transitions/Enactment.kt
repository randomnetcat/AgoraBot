package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerFascistPower
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPoliciesState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPolicyType

sealed class SecretHitlerEnactmentResult {
    sealed class GameContinues : SecretHitlerEnactmentResult() {
        data class NoPower(override val newGlobalState: SecretHitlerGlobalGameState) : GameContinues()

        data class PowerActivated(
            override val newGlobalState: SecretHitlerGlobalGameState,
            val power: SecretHitlerFascistPower,
        ) : GameContinues()

        abstract val newGlobalState: SecretHitlerGlobalGameState
    }

    data class GameEnds(val winResult: SecretHitlerWinResult) : SecretHitlerEnactmentResult()
}

private fun SecretHitlerGlobalGameState.withPoliciesState(
    newPoliciesState: SecretHitlerPoliciesState,
): SecretHitlerGlobalGameState {
    return this.copy(
        boardState = this.boardState.copy(
            policiesState = newPoliciesState,
        ),
    )
}

fun SecretHitlerGlobalGameState.afterEnacting(policyType: SecretHitlerPolicyType): SecretHitlerEnactmentResult {
    val nestedResult = this.boardState.policiesState.withPolicyEnacted(
        config = this.configuration,
        type = policyType,
    )

    return when (nestedResult) {
        is SecretHitlerPoliciesState.EnactmentResult.GameContinues -> {
            val newGlobalState = this.withPoliciesState(nestedResult.newPolicyState)

            if (nestedResult.fascistPower != null) {
                SecretHitlerEnactmentResult.GameContinues.PowerActivated(
                    newGlobalState = newGlobalState,
                    power = nestedResult.fascistPower,
                )
            } else {
                SecretHitlerEnactmentResult.GameContinues.NoPower(
                    newGlobalState = newGlobalState,
                )
            }
        }

        is SecretHitlerPoliciesState.EnactmentResult.GameEnds -> {
            SecretHitlerEnactmentResult.GameEnds(nestedResult.winResult)
        }
    }
}
