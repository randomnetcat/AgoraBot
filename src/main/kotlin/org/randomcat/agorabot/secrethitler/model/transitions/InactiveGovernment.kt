package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.*

sealed class SecretHitlerInactiveGovernmentResult {
    data class NewElection(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
    ) : SecretHitlerInactiveGovernmentResult()

    sealed class CountryInChaos : SecretHitlerInactiveGovernmentResult() {
        data class GameContinues(
            val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
            val drawnPolicyType: SecretHitlerPolicyType,
            val shuffledDeck: Boolean,
        ) : CountryInChaos()

        data class GameEnds(val winResult: SecretHitlerWinResult) : CountryInChaos()
    }
}

private fun <E : SecretHitlerEphemeralState> SecretHitlerGameState.Running.With<E>.withNewTrackerValue(
    newTrackerState: Int,
): SecretHitlerGameState.Running.With<E> {
    return withGlobal(
        newGlobalState = this.globalState.copy(
            electionState = this.globalState.electionState.copy(
                electionTrackerState = newTrackerState,
            ),
        ),
    )
}

private fun <E : SecretHitlerEphemeralState> SecretHitlerGameState.Running.With<E>.withTrackerReset(
): SecretHitlerGameState.Running.With<E> {
    return withNewTrackerValue(0)
}

private fun <E : SecretHitlerEphemeralState> SecretHitlerGameState.Running.With<E>.withIncrementedElectionTracker(
): SecretHitlerGameState.Running.With<E> {
    val oldTrackerState = this.globalState.electionState.electionTrackerState
    require(oldTrackerState != Int.MAX_VALUE)

    val newTrackerState = oldTrackerState + 1
    check(newTrackerState < this.globalState.configuration.speedyEnactRequirement)

    return withNewTrackerValue(newTrackerState)
}

private fun <E : SecretHitlerEphemeralState> SecretHitlerGameState.Running.With<E>.withNoTermLimits(
): SecretHitlerGameState.Running.With<E> {
    return this.withGlobal(
        newGlobalState = this.globalState.copy(
            electionState = this.globalState.electionState.copy(
                termLimitState = SecretHitlerTermLimitState.noLimits(),
            ),
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
                    .withGlobal(enactResult.newGlobalState)
                    .afterAdvancingTickerAndNewElection()
                    .withTrackerReset()
                    .withNoTermLimits(),
                drawnPolicyType = drawResult.drawnCard,
                shuffledDeck = drawResult.shuffled,
            )
        }

        is SecretHitlerSpeedyEnactResult.GameEnds -> {
            SecretHitlerInactiveGovernmentResult.CountryInChaos.GameEnds(winResult = enactResult.winResult)
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
        newState = this.afterAdvancingTickerAndNewElection().withIncrementedElectionTracker(),
    )
}

