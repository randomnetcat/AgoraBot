package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerFascistPower
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPolicyType
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

sealed class SecretHitlerAfterChancellorPolicySelectedResult {
    sealed class StatelessPowerKind {
        object PolicyPeek : StatelessPowerKind()
    }

    abstract val policyType: SecretHitlerPolicyType

    sealed class GameContinues : SecretHitlerAfterChancellorPolicySelectedResult() {
        sealed class WithPower : GameContinues() {
            abstract val power: SecretHitlerFascistPower

            override val policyType: SecretHitlerPolicyType
                get() = SecretHitlerPolicyType.FASCIST

            data class Stateful(
                override val newState: GameState.Running.With<EphemeralState.PolicyPending>,
                override val power: SecretHitlerFascistPower,
            ) : WithPower()

            data class Stateless(
                override val newState: GameState.Running.With<EphemeralState.ChancellorSelectionPending>,
                override val power: SecretHitlerFascistPower,
                val powerResult: StatelessPowerKind,
            ) : WithPower()
        }

        data class NoPower(
            override val newState: GameState.Running.With<EphemeralState.ChancellorSelectionPending>,
            override val policyType: SecretHitlerPolicyType,
        ) : GameContinues()

        abstract val newState: GameState.Running
    }

    data class GameEnds(
        val winResult: SecretHitlerWinResult,
        override val policyType: SecretHitlerPolicyType,
    ) : SecretHitlerAfterChancellorPolicySelectedResult()
}

private fun handleGameContinuing(
    enactResult: SecretHitlerEnactmentResult.GameContinues,
    presidentNumber: SecretHitlerPlayerNumber,
    policyType: SecretHitlerPolicyType,
): SecretHitlerAfterChancellorPolicySelectedResult.GameContinues {
    // Returns the state to use if the result is to advance to the next election.
    fun stateForNewElection() = enactResult.newGlobalState.stateForElectionAfterAdvancingTicker()

    return when (enactResult) {
        // If there's no power, immediately advance to the next election.
        is SecretHitlerEnactmentResult.GameContinues.NoPower -> {
            SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.NoPower(
                newState = stateForNewElection(),
                policyType = policyType,
            )
        }

        is SecretHitlerEnactmentResult.GameContinues.PowerActivated -> {
            check(policyType == SecretHitlerPolicyType.FASCIST)

            val fascistPowerResult = secretHitlerFascistPowerEphemeralState(
                fascistPower = enactResult.power,
                presidentNumber = presidentNumber,
            )

            // Some powers are stateful (they require input from the President), so they have a dedicated ephemeral
            // state; for those, transition to the pending state.
            //
            // For powers that are not stateful, immediately advance to the next election, but set a flag so that
            // the caller can handle the events for the power (for instance, for PolicyPeek, by sending a private
            // message to the President).
            when (fascistPowerResult) {
                is SecretHitlerFascistPowerStateResult.Stateful -> {
                    SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.WithPower.Stateful(
                        newState = GameState.Running(
                            enactResult.newGlobalState,
                            fascistPowerResult.ephemeralState,
                        ),
                        power = enactResult.power,
                    )
                }

                is SecretHitlerFascistPowerStateResult.PolicyPeek -> {
                    SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.WithPower.Stateless(
                        newState = stateForNewElection(),
                        power = enactResult.power,
                        powerResult = SecretHitlerAfterChancellorPolicySelectedResult.StatelessPowerKind.PolicyPeek,
                    )
                }
            }
        }
    }
}

fun GameState.Running.With<EphemeralState.ChancellorPolicyChoicePending>.afterChancellorPolicySelected(
    policyIndex: Int,
): SecretHitlerAfterChancellorPolicySelectedResult {
    require(policyIndex < ephemeralState.options.policies.size)

    val policyType = ephemeralState.options.policies[policyIndex]

    return when (val enactResult = globalState.afterEnacting(policyType)) {
        is SecretHitlerEnactmentResult.GameContinues -> {
            handleGameContinuing(
                enactResult = enactResult,
                presidentNumber = ephemeralState.governmentMembers.president,
                policyType = policyType,
            )
        }

        is SecretHitlerEnactmentResult.GameEnds -> {
            SecretHitlerAfterChancellorPolicySelectedResult.GameEnds(
                winResult = enactResult.winResult,
                policyType = policyType,
            )
        }
    }
}
