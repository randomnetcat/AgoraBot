package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

enum class SecretHitlerParty {
    FASCIST,
    LIBERAL,
}

sealed class SecretHitlerRole(val party: SecretHitlerParty) {
    object Liberal : SecretHitlerRole(SecretHitlerParty.LIBERAL)

    sealed class FascistParty : SecretHitlerRole(SecretHitlerParty.FASCIST)
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

    fun toMap(): Map<SecretHitlerPlayerNumber, SecretHitlerRole> {
        return rolesByPlayer
    }
}

fun SecretHitlerRoleMap.playerIsLiberal(player: SecretHitlerPlayerNumber) = liberalPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsPlainFascist(player: SecretHitlerPlayerNumber) = plainFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsAnyFascist(player: SecretHitlerPlayerNumber) = allFascistPlayers.contains(player)
fun SecretHitlerRoleMap.playerIsHitler(player: SecretHitlerPlayerNumber) = hitlerPlayer == player
