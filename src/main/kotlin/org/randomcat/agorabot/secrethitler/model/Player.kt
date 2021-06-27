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

data class SecretHitlerPlayerMap(
    private val players: ImmutableMap<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>,
) {
    constructor(players: Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName>) : this(players.toImmutableMap())

    init {
        require(players.values.isDistinct())
    }

    companion object {
        fun fromNames(name: Set<SecretHitlerPlayerExternalName>): SecretHitlerPlayerMap {
            return SecretHitlerPlayerMap(
                players = name
                    .mapIndexed { index, externalName ->
                        SecretHitlerPlayerNumber(index) to externalName
                    }
                    .toMap(),
            )
        }
    }

    fun playerByNumber(number: SecretHitlerPlayerNumber): SecretHitlerPlayerExternalName? {
        return players[number]
    }

    fun playerByNumberKnown(number: SecretHitlerPlayerNumber): SecretHitlerPlayerExternalName {
        return playerByNumber(number) ?: error("Incorrectly assumed that player number ${number.raw} exists")
    }

    fun numberByPlayer(playerName: SecretHitlerPlayerExternalName): SecretHitlerPlayerNumber? {
        return players.entries.firstOrNull { it.value == playerName }?.key
    }

    fun toMap(): Map<SecretHitlerPlayerNumber, SecretHitlerPlayerExternalName> {
        return players
    }

    val validNumbers: Set<SecretHitlerPlayerNumber> = players.keys
    val playerCount: Int = validNumbers.size

    val minNumber: SecretHitlerPlayerNumber = validNumbers.minByOrNull { it.raw } ?: error("expected number")
    val maxNumber: SecretHitlerPlayerNumber = validNumbers.maxByOrNull { it.raw } ?: error("expected number")

    fun circularNumberAfter(number: SecretHitlerPlayerNumber): SecretHitlerPlayerNumber {
        return validNumbers.filter { it.raw > number.raw }.minByOrNull { it.raw } ?: minNumber
    }
}
