package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
