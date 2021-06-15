package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SecretHitlerGameId(val raw: String)

data class SecretHitlerGlobalGameState(
    val configuration: SecretHitlerGameConfiguration,
    val playerMap: SecretHitlerPlayerMap,
    val roleMap: SecretHitlerRoleMap,
    val boardState: SecretHitlerBoardState,
    val electionState: SecretHitlerElectionState,
) {
    init {
        require(playerMap.validNumbers == roleMap.assignedPlayers) {
            "All player numbers should have exactly one role. " +
                    "Players: ${playerMap.validNumbers}. Assigned roles: ${roleMap.assignedPlayers}"
        }
    }
}

sealed class SecretHitlerGameState {
    data class Joining(val playerNames: ImmutableSet<SecretHitlerPlayerExternalName>) : SecretHitlerGameState() {
        constructor() : this(persistentSetOf())

        fun withNewPlayer(player: SecretHitlerPlayerExternalName): SecretHitlerGameState.Joining {
            return SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().add(player))
        }

        fun withoutPlayer(player: SecretHitlerPlayerExternalName): SecretHitlerGameState.Joining {
            return SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().remove(player))
        }
    }

    data class Running(
        val globalState: SecretHitlerGlobalGameState,
    )
}
