package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.*
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SecretHitlerGameId(val raw: String)

@JvmInline
value class SecretHitlerPlayerNumber(val raw: Int)

@JvmInline
value class SecretHitlerPlayerExternalName(val raw: String)

data class SecretHitlerPlayerMap(
    private val players: ImmutableList<SecretHitlerPlayerExternalName>,
) {
    constructor(players: List<SecretHitlerPlayerExternalName>) : this(players.toImmutableList())

    fun playerByNumber(number: SecretHitlerPlayerNumber): SecretHitlerPlayerExternalName {
        return players[number.raw]
    }

    fun numberByPlayer(playerName: SecretHitlerPlayerExternalName): SecretHitlerPlayerNumber {
        return SecretHitlerPlayerNumber(players.indexOf(playerName))
    }

    val validNumbers: Set<SecretHitlerPlayerNumber> = players.indices.map { SecretHitlerPlayerNumber(it) }.toSet()
}

sealed class SecretHitlerRole {
    object Liberal : SecretHitlerRole()

    sealed class FascistParty : SecretHitlerRole()
    object PlainFascist : FascistParty()
    object Hitler : FascistParty()
}

data class SecretHitlerRoleMap(
    private val rolesByPlayer: ImmutableMap<SecretHitlerPlayerNumber, SecretHitlerRole>,
) {
    constructor(rolesByPlayer: Map<SecretHitlerPlayerNumber, SecretHitlerRole>) : this(rolesByPlayer.toImmutableMap())

    init {
        val hitlerPlayers = rolesByPlayer.filter { it.value is SecretHitlerRole.Hitler }.map { it.key }

        require(hitlerPlayers.count() == 1) {
            "There should be exactly one Hitler but actually got: $hitlerPlayers"
        }
    }

    val assignedPlayers: Set<SecretHitlerPlayerNumber> = rolesByPlayer.keys

    fun roleOf(playerNumber: SecretHitlerPlayerNumber): SecretHitlerRole {
        return rolesByPlayer.getValue(playerNumber)
    }

    private inline fun <reified Role : SecretHitlerRole> playersWithRole(): Set<SecretHitlerPlayerNumber> {
        return rolesByPlayer.filter { it.value is Role }.keys
    }

    val liberalPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.Liberal>()
    val allFascistPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.FascistParty>()
    val plainFascistPlayers: Set<SecretHitlerPlayerNumber> = playersWithRole<SecretHitlerRole.PlainFascist>()

    val hitlerPlayer: SecretHitlerPlayerNumber = playersWithRole<SecretHitlerRole.Hitler>().single()
}

fun SecretHitlerRoleMap.playerIsLiberal(player: SecretHitlerPlayerNumber) = liberalPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsPlainFascist(player: SecretHitlerPlayerNumber) = plainFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsAnyFascist(player: SecretHitlerPlayerNumber) = allFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsHitler(player: SecretHitlerPlayerNumber) = hitlerPlayer == player

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
        val playerMap: SecretHitlerPlayerMap,
        val roleMap: SecretHitlerRoleMap,
    ) {
        init {
            require(playerMap.validNumbers == roleMap.assignedPlayers) {
                "All player numbers should have exactly one role. " +
                        "Players: ${playerMap.validNumbers}. Assigned roles: ${roleMap.assignedPlayers}"
            }
        }
    }
}
