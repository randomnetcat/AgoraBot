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

private fun SecretHitlerGlobalGameState.afterChaos(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerInactiveGovernmentResult.CountryInChaos {
    val drawResult = this.boardState.deckState.drawDeck.drawSingle()

    val stateWithNewDeck = this.withDeckState(
        SecretHitlerDeckState(
            drawDeck = drawResult.newDeck,
            discardDeck = this.boardState.deckState.discardDeck,
        ).shuffledIfDrawPileSmall(
            shuffleProvider = shuffleProvider,
        ),
    )

    return when (val enactResult = stateWithNewDeck.afterSpeedyEnacting(drawResult.drawnCard)) {
        is SecretHitlerSpeedyEnactResult.GameContinues -> {
            SecretHitlerInactiveGovernmentResult.CountryInChaos.GameContinues(
                newState = enactResult
                    .newGlobalState
                    .withElectionTrackerReset()
                    .withNoTermLimits()
                    .stateForElectionAfterAdvancingTicker(),
                drawnPolicyType = drawResult.drawnCard,
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

fun SecretHitlerGlobalGameState.afterInactiveGovernment(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerInactiveGovernmentResult {
    if (electionState.electionTrackerState == (configuration.speedyEnactRequirement - 1)) {
        return afterChaos(shuffleProvider = shuffleProvider)
    }

    return SecretHitlerInactiveGovernmentResult.NewElection(
        newState = this.stateForElectionAfterAdvancingTicker().let {
            it.withGlobal(it.globalState.withIncrementedElectionTracker())
        },
    )
}

