package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

data class SecretHitlerElectionState(
    val currentPresidentTicker: SecretHitlerPlayerNumber,
    val termLimitedPlayers: ImmutableList<SecretHitlerPlayerNumber>,
) {
    constructor(
        currentPresidentTicker: SecretHitlerPlayerNumber,
        termLimitedPlayers: List<SecretHitlerPlayerNumber>,
    ) : this(
        currentPresidentTicker = currentPresidentTicker,
        termLimitedPlayers = termLimitedPlayers.toImmutableList(),
    )

    companion object {
        fun forInitialPresident(firstPresident: SecretHitlerPlayerNumber): SecretHitlerElectionState {
            return SecretHitlerElectionState(
                currentPresidentTicker = firstPresident,
                termLimitedPlayers = persistentListOf(),
            )
        }
    }
}
