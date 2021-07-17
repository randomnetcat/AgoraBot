package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

fun SecretHitlerGlobalGameState.stateForNewElectionWith(
    president: SecretHitlerPlayerNumber,
): GameState.Running.With<EphemeralState.ChancellorSelectionPending> {
    return GameState.Running(
        globalState = this,
        ephemeralState = EphemeralState.ChancellorSelectionPending(
            presidentCandidate = president,
        ),
    )
}

private fun GameState.Running.With<EphemeralState.ChancellorSelectionPending>.withTickerValue(
    nextTickerValue: SecretHitlerPlayerNumber,
): GameState.Running.With<EphemeralState.ChancellorSelectionPending> {
    return this.withGlobal(
        newGlobalState = this.globalState.copy(
            electionState = this.globalState.electionState.copy(
                currentPresidentTicker = nextTickerValue,
            ),
        ),
    )
}

fun SecretHitlerGlobalGameState.stateForElectionAfterAdvancingTicker(
): GameState.Running.With<EphemeralState.ChancellorSelectionPending> {
    val nextTickerValue = playerMap.circularNumberAfter(electionState.currentPresidentTicker)
    return stateForNewElectionWith(president = nextTickerValue).withTickerValue(nextTickerValue)
}
