package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState.ChancellorSelectionPending
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState.Running
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

fun Running.afterNewElectionWith(president: SecretHitlerPlayerNumber): Running.With<ChancellorSelectionPending> {
    return withEphemeral(
        ChancellorSelectionPending(
            presidentCandidate = president,
        )
    )
}

private fun Running.With<ChancellorSelectionPending>.withTickerValue(
    nextTickerValue: SecretHitlerPlayerNumber,
): Running.With<ChancellorSelectionPending> {
    return this.withGlobal(
        newGlobalState = this.globalState.copy(
            electionState = this.globalState.electionState.copy(
                currentPresidentTicker = nextTickerValue,
            ),
        ),
    )
}

fun Running.afterAdvancingTickerAndNewElection(): Running.With<ChancellorSelectionPending> {
    val nextTickerValue = globalState.playerMap.circularNumberAfter(globalState.electionState.currentPresidentTicker)
    return afterNewElectionWith(president = nextTickerValue).withTickerValue(nextTickerValue)
}
