package org.randomcat.agorabot.secrethitler.model

data class SecretHitlerGovernmentMembers(
    val president: SecretHitlerPlayerNumber,
    val chancellor: SecretHitlerPlayerNumber,
)


data class SecretHitlerTermLimitState(
    val termLimitedGovernment: SecretHitlerGovernmentMembers?,
) {
    companion object {
        fun noLimits() = SecretHitlerTermLimitState(termLimitedGovernment = null)
    }
}

data class SecretHitlerElectionState(
    /**
     * The ticker has the player number of the President for whom the most recent non-special election has begun.
     *
     * Specifically, for the first Presidential candidate, it is initialized to that candidate's number. When a normal
     * election begins after that, the ticker is incremented (wrapping around if it would then exceed the highest player
     * number), then the resulting player is the new Presidential candidate. For a special election, the state is moved
     * to an election without updating the ticker; for the next normal election, the normal rotation will resume.
     */
    val currentPresidentTicker: SecretHitlerPlayerNumber,
    val termLimitState: SecretHitlerTermLimitState,
    val electionTrackerState: Int,
) {
    init {
        require(electionTrackerState >= 0)
    }

    companion object {
        fun forInitialPresident(firstPresident: SecretHitlerPlayerNumber): SecretHitlerElectionState {
            return SecretHitlerElectionState(
                currentPresidentTicker = firstPresident,
                termLimitState = SecretHitlerTermLimitState.noLimits(),
                electionTrackerState = 0,
            )
        }
    }
}
