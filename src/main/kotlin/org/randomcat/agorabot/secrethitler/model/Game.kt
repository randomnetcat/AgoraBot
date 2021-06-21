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

        init {
            require(playerNames.size <= SECRET_HITLER_MAX_START_PLAYERS)
        }

        sealed class TryJoinResult {
            data class Success(val newState: Joining) : TryJoinResult()
            object Full : TryJoinResult()
            object AlreadyJoined : TryJoinResult()
        }

        fun tryWithNewPlayer(player: SecretHitlerPlayerExternalName): TryJoinResult {
            return when {
                playerNames.contains(player) -> {
                    TryJoinResult.AlreadyJoined
                }

                playerNames.size >= SECRET_HITLER_MAX_START_PLAYERS -> {
                    TryJoinResult.Full
                }

                else -> {
                    TryJoinResult.Success(
                        SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().add(player)),
                    )
                }
            }
        }

        sealed class TryLeaveResult {
            data class Success(val newState: Joining) : TryLeaveResult()
            object NotPlayer : TryLeaveResult()
        }

        fun tryWithoutPlayer(player: SecretHitlerPlayerExternalName): TryLeaveResult {
            return when {
                !playerNames.contains(player) -> {
                    TryLeaveResult.NotPlayer
                }

                else -> {
                    TryLeaveResult.Success(
                        SecretHitlerGameState.Joining(playerNames = playerNames.toPersistentSet().remove(player)),
                    )
                }
            }
        }
    }

    data class Running(
        val globalState: SecretHitlerGlobalGameState,
    ) : SecretHitlerGameState()
}
