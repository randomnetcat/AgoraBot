package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.randomcat.util.isDistinct

@JvmInline
value class SecretHitlerPlayerNumber(val raw: Int)

@JvmInline
value class SecretHitlerPlayerExternalName(val raw: String)

data class SecretHitlerPlayerMap(
    private val players: ImmutableMap<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>,
) {
    constructor(players: Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>) : this(players.toImmutableMap())

    init {
        require(players.values.isDistinct())
    }

    fun playerByNumber(number: SecretHitlerPlayerNumber): SecretHitlerPlayerExternalName {
        return players.getValue(number)
    }

    fun numberByPlayer(playerName: SecretHitlerPlayerExternalName): SecretHitlerPlayerNumber {
        return players.entries.single { it.value == playerName }.key
    }

    fun toMap(): Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName> {
        return players
    }

    val validNumbers: Set<SecretHitlerPlayerNumber> = players.keys
}
