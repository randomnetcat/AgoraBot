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
}

interface SecretHitlerGameList {
    fun gameById(id: SecretHitlerGameId): SecretHitlerGameState?

    fun createGame(state: SecretHitlerGameState): SecretHitlerGameId
    fun removeGameIfExists(id: SecretHitlerGameId)

    /**
     * If [id] denotes an existing game, updates it by using [mapper], then returns true. Otherwise, returns false.
     */
    fun updateGame(id: SecretHitlerGameId, mapper: (SecretHitlerGameState) -> SecretHitlerGameState): Boolean
}

inline fun <reified T : SecretHitlerGameState, R> SecretHitlerGameList.updateGameTyped(
    id: SecretHitlerGameId,
    onNoSuchGame: () -> R,
    onInvalidType: (invalidGame: SecretHitlerGameState) -> R,
    crossinline validMapper: (validGame: T) -> SecretHitlerGameState,
    afterValid: () -> R,
): R {
    var invalidGame: SecretHitlerGameState? = null

    val updated = updateGame(id) { gameState ->
        if (gameState is T) {
            invalidGame = null
            validMapper(gameState)
        } else {
            invalidGame = gameState
            gameState
        }
    }

    val finalInvalidGame = invalidGame

    return when {
        finalInvalidGame != null -> {
            onInvalidType(finalInvalidGame)
        }

        updated -> {
            afterValid()
        }

        else -> {
            onNoSuchGame()
        }
    }
}

interface SecretHitlerChannelGameMap {
    fun gameByChannelId(channelId: String): SecretHitlerGameId?

    /**
     * Attempts to set the game id for [channelId] to [gameId], failing if the channel already has a game id set.
     * @return true if the game id is successfully set, false otherwise
     */
    fun tryPutGameForChannelId(channelId: String, gameId: SecretHitlerGameId): Boolean

    /**
     * Attempts to remove the entry for the channel with id [channelId]. If it existed, returns the game id that was
     * associated with that channel; otherwise, returns null.
     */
    fun removeGameForChannelId(channelId: String): SecretHitlerGameId?
}

data class SecretHitlerRepository(
    val gameList: SecretHitlerGameList,
    val channelGameMap: SecretHitlerChannelGameMap,
)
