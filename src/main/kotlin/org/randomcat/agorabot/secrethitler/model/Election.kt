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
    val currentPresidentTicker: SecretHitlerPlayerNumber,
    val termLimitState: SecretHitlerTermLimitState,
) {
    companion object {
        fun forInitialPresident(firstPresident: SecretHitlerPlayerNumber): SecretHitlerElectionState {
            return SecretHitlerElectionState(
                currentPresidentTicker = firstPresident,
                termLimitState = SecretHitlerTermLimitState.noLimits(),
            )
        }
    }
}
