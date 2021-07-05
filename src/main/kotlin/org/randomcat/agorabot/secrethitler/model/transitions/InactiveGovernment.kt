package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.*

sealed class SecretHitlerInactiveGovernmentResult {
    data class NewElection(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
    ) : SecretHitlerInactiveGovernmentResult()

    sealed class CountryInChaos : SecretHitlerInactiveGovernmentResult() {
        abstract val drawnPolicyType: SecretHitlerPolicyType

        data class GameContinues(
            val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
            override val drawnPolicyType: SecretHitlerPolicyType,
            val shuffledDeck: Boolean,
        ) : CountryInChaos()

        data class GameEnds(
            val winResult: SecretHitlerWinResult,
            override val drawnPolicyType: SecretHitlerPolicyType,
        ) : CountryInChaos()
    }
}

private fun SecretHitlerGlobalGameState.withNoTermLimits(): SecretHitlerGlobalGameState {
    return this.copy(
        electionState = this.electionState.copy(
            termLimitState = SecretHitlerTermLimitState.noLimits(),
        ),
    )
}

private fun SecretHitlerGameState.Running.afterChaos(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerInactiveGovernmentResult.CountryInChaos {
    val drawResult = this.globalState.boardState.deckState.drawSingle(shuffleProvider = shuffleProvider)

    return when (val enactResult = this.globalState.afterSpeedyEnacting(drawResult.drawnCard)) {
        is SecretHitlerSpeedyEnactResult.GameContinues -> {
            SecretHitlerInactiveGovernmentResult.CountryInChaos.GameContinues(
                newState = this
                    .withGlobal(
                        enactResult
                            .newGlobalState
                            .withElectionTrackerReset()
                            .withNoTermLimits()
                    )
                    .afterAdvancingTickerAndNewElection(),
                drawnPolicyType = drawResult.drawnCard,
                shuffledDeck = drawResult.shuffled,
            )
        }

        is SecretHitlerSpeedyEnactResult.GameEnds -> {
            SecretHitlerInactiveGovernmentResult.CountryInChaos.GameEnds(
                winResult = enactResult.winResult,
                drawnPolicyType = drawResult.drawnCard,
            )
        }
    }
}

fun SecretHitlerGameState.Running.afterInactiveGovernment(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerInactiveGovernmentResult {
    if (globalState.electionState.electionTrackerState == (globalState.configuration.speedyEnactRequirement - 1)) {
        return afterChaos(shuffleProvider = shuffleProvider)
    }

    return SecretHitlerInactiveGovernmentResult.NewElection(
        newState = this.afterAdvancingTickerAndNewElection().let {
            it.withGlobal(it.globalState.withIncrementedElectionTracker())
        },
    )
}

