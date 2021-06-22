package org.randomcat.agorabot.secrethitler

import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState

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

internal val SH_VALID_EXTRACT_NOT_SET = Any()

internal inline fun <reified T : SecretHitlerGameState, VE, R> SecretHitlerGameList.updateGameTypedWithValidExtract(
    id: SecretHitlerGameId,
    onNoSuchGame: () -> R,
    onInvalidType: (invalidGame: SecretHitlerGameState) -> R,
    crossinline validMapper: (validGame: T) -> Pair<SecretHitlerGameState, VE>,
    afterValid: (VE) -> R,
): R {
    var validExtractValue: Any? = SH_VALID_EXTRACT_NOT_SET

    return updateGameTyped(
        id = id,
        onNoSuchGame = {
            validExtractValue = SH_VALID_EXTRACT_NOT_SET
            onNoSuchGame()
        },
        onInvalidType = { invalidGame ->
            validExtractValue = SH_VALID_EXTRACT_NOT_SET
            onInvalidType(invalidGame)
        },
        validMapper = { validGame: T ->
            val result = validMapper(validGame)
            validExtractValue = result.second
            result.first
        },
        afterValid = {
            check(validExtractValue !== SH_VALID_EXTRACT_NOT_SET)
            afterValid(validExtractValue as VE)
        },
    )
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
