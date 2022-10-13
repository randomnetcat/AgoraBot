package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState

private fun SecretHitlerGlobalGameState.withNewTrackerValue(
    newTrackerState: Int,
): SecretHitlerGlobalGameState {
    return this.copy(
        electionState = this.electionState.copy(
            electionTrackerState = newTrackerState,
        ),
    )
}

fun SecretHitlerGlobalGameState.withElectionTrackerReset(): SecretHitlerGlobalGameState {
    return withNewTrackerValue(0)
}

fun SecretHitlerGlobalGameState.withIncrementedElectionTracker(): SecretHitlerGlobalGameState {
    val oldTrackerState = electionState.electionTrackerState
    require(oldTrackerState != Int.MAX_VALUE)

    val newTrackerState = oldTrackerState + 1
    check(newTrackerState < configuration.speedyEnactRequirement)

    return withNewTrackerValue(newTrackerState)
}
