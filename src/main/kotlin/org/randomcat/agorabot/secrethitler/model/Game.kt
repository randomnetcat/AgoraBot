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

    companion object {
        fun <Ephemeral : SecretHitlerEphemeralState> Running(
            globalState: SecretHitlerGlobalGameState,
            ephemeralState: Ephemeral,
        ): Running.With<Ephemeral> {
            return Running.With(
                globalState = globalState,
                ephemeralState = ephemeralState,
            )
        }
    }

    sealed class Running : SecretHitlerGameState() {
        abstract val globalState: SecretHitlerGlobalGameState
        abstract val ephemeralState: SecretHitlerEphemeralState

        fun <Ephemeral : SecretHitlerEphemeralState> withEphemeral(
            newEphemeralState: Ephemeral,
        ): Running.With<Ephemeral> {
            return when (this) {
                is With<*> -> With(
                    globalState = globalState,
                    ephemeralState = newEphemeralState,
                )
            }
        }

        inline fun <reified Ephemeral : SecretHitlerEphemeralState> tryWith(): With<Ephemeral>? {
            return when (this) {
                is With<*> -> {
                    if (ephemeralState is Ephemeral) {
                        @Suppress("UNCHECKED_CAST")
                        this as With<Ephemeral>
                    } else {
                        null
                    }
                }
            }
        }

        inline fun <reified Ephemeral : SecretHitlerEphemeralState> assumeWith(): With<Ephemeral> {
            return checkNotNull(this.tryWith<Ephemeral>()) {
                "Incorrect assumption of ephemeral state ${Ephemeral::class.qualifiedName}"
            }
        }

        data class With<Ephemeral : SecretHitlerEphemeralState>(
            override val globalState: SecretHitlerGlobalGameState,
            override val ephemeralState: Ephemeral,
        ) : Running()

        sealed class StartResult {
            data class Success(
                val newState: Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
                val configuration: SecretHitlerStartConfiguration,
            ) : StartResult()

            sealed class Failure : StartResult()
            object InsufficientPlayers : Failure()
        }

        companion object {
            fun tryStart(
                currentState: Joining,
                assignRoles: (Set<SecretHitlerPlayerNumber>, SecretHitlerRoleConfiguration) -> SecretHitlerRoleMap,
                shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
            ): StartResult {
                val playerNames = currentState.playerNames
                if (playerNames.size < SECRET_HITLER_MIN_START_PLAYERS) return StartResult.InsufficientPlayers

                val startConfiguration = currentState.startConfiguration()
                val playerMap = SecretHitlerPlayerMap.fromNames(playerNames)
                val roleMap = assignRoles(playerMap.validNumbers, startConfiguration.roleConfiguration)
                check(roleMap.assignedPlayers == playerMap.validNumbers)

                val firstPresident = playerMap.minNumber

                return StartResult.Success(
                    newState = Running(
                        globalState = SecretHitlerGlobalGameState(
                            configuration = startConfiguration.gameConfiguration,
                            playerMap = playerMap,
                            roleMap = roleMap,
                            boardState = SecretHitlerBoardState(
                                deckState = shuffleProvider.newDeck(),
                                policiesState = SecretHitlerPoliciesState(),
                            ),
                            electionState = SecretHitlerElectionState.forInitialPresident(firstPresident),
                        ),
                        ephemeralState = SecretHitlerEphemeralState.ChancellorSelectionPending(
                            presidentCandidate = firstPresident,
                        ),
                    ),
                    configuration = startConfiguration,
                )
            }
        }
    }

    object Completed : SecretHitlerGameState()
}
