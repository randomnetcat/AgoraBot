package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

data class SecretHitlerPowersState(
    val previouslyInvestigatedPlayers: ImmutableSet<SecretHitlerPlayerNumber>,
) {
    companion object {
        fun initial(): SecretHitlerPowersState {
            return SecretHitlerPowersState(
                previouslyInvestigatedPlayers = persistentSetOf(),
            )
        }
    }

    fun afterInvestigationOf(investigatedPlayer: SecretHitlerPlayerNumber): SecretHitlerPowersState {
        return SecretHitlerPowersState(
            previouslyInvestigatedPlayers = this
                .previouslyInvestigatedPlayers
                .toPersistentSet()
                .add(investigatedPlayer),
        )
    }
}
