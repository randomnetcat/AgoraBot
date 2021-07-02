package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

fun GameState.Running.afterNewElectionWith(
    president: SecretHitlerPlayerNumber,
): GameState.Running.With<EphemeralState.ChancellorSelectionPending> {
    return withEphemeral(
        EphemeralState.ChancellorSelectionPending(
            presidentCandidate = president,
        )
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

fun GameState.Running.afterAdvancingTickerAndNewElection(
): GameState.Running.With<EphemeralState.ChancellorSelectionPending> {
    val nextTickerValue = globalState.playerMap.circularNumberAfter(globalState.electionState.currentPresidentTicker)
    return afterNewElectionWith(president = nextTickerValue).withTickerValue(nextTickerValue)
}
