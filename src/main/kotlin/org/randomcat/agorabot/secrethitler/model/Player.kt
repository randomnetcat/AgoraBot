package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import org.randomcat.util.isDistinct

@JvmInline
@Serializable
value class SecretHitlerPlayerNumber(val raw: Int)

@JvmInline
@Serializable
value class SecretHitlerPlayerExternalName(val raw: String)

const val SECRET_HITLER_MIN_PLAYERS = 5
const val SECRET_HITLER_MAX_PLAYERS = 10

data class SecretHitlerPlayerMap(
    private val players: ImmutableMap<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>,
) {
    constructor(players: Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>) : this(players.toImmutableMap())

    init {
        require(players.values.isDistinct())
        require(players.size >= SECRET_HITLER_MIN_PLAYERS)
        require(players.size <= SECRET_HITLER_MAX_PLAYERS)
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
