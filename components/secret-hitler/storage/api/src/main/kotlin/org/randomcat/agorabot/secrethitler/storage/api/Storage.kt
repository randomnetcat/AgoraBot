package org.randomcat.agorabot.secrethitler.storage.api

import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
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
        !updated -> {
            onNoSuchGame()
        }

        finalInvalidGame != null -> {
            onInvalidType(finalInvalidGame)
        }

        else -> {
            afterValid()
        }
    }
}

@PublishedApi
internal object SecretHitlerValidExtractNotSet

inline fun <reified T : SecretHitlerGameState, VE, R> SecretHitlerGameList.updateGameTypedWithValidExtract(
    id: SecretHitlerGameId,
    onNoSuchGame: () -> R,
    onInvalidType: (invalidGame: SecretHitlerGameState) -> R,
    crossinline validMapper: (validGame: T) -> Pair<SecretHitlerGameState, VE>,
    afterValid: (VE) -> R,
): R {
    var validExtractValue: Any? = SecretHitlerValidExtractNotSet

    return updateGameTyped(
        id = id,
        onNoSuchGame = {
            validExtractValue = SecretHitlerValidExtractNotSet
            onNoSuchGame()
        },
        onInvalidType = { invalidGame ->
            validExtractValue = SecretHitlerValidExtractNotSet
            onInvalidType(invalidGame)
        },
        validMapper = { validGame: T ->
            val result = validMapper(validGame)
            validExtractValue = result.second
            result.first
        },
        afterValid = {
            check(validExtractValue !== SecretHitlerValidExtractNotSet)

            @Suppress("UNCHECKED_CAST")
            afterValid(validExtractValue as VE)
        },
    )
}

@PublishedApi
internal object SecretHitlerInvalidRunningGameMarker

inline fun <reified E : SecretHitlerEphemeralState, VE, R> SecretHitlerGameList.updateRunningGameWithValidExtract(
    id: SecretHitlerGameId,
    onNoSuchGame: () -> R,
    onInvalidType: () -> R, // Does not accept game in order to ease implementation. May revisit later.
    crossinline validMapper: (validGame: SecretHitlerGameState.Running.With<E>) -> Pair<SecretHitlerGameState, VE>,
    afterValid: (VE) -> R,
): R {
    return updateGameTypedWithValidExtract(
        id = id,
        onNoSuchGame = onNoSuchGame,
        onInvalidType = { onInvalidType() },
        validMapper = { runningGame: SecretHitlerGameState.Running ->
            val typedGame = runningGame.tryWith<E>()

            if (typedGame == null) {
                runningGame to SecretHitlerInvalidRunningGameMarker
            } else {
                validMapper(typedGame)
            }
        },
        afterValid = { validExtract: Any? ->
            if (validExtract != SecretHitlerInvalidRunningGameMarker) {
                @Suppress("UNCHECKED_CAST")
                afterValid(validExtract as VE)
            } else {
                onInvalidType()
            }
        },
    )
}

sealed class SecretHitlerUpdateValidationResult<out ErrorResult, out ValidResult> {
    data class Invalid<out ErrorResult>(
        val result: ErrorResult,
    ) : SecretHitlerUpdateValidationResult<ErrorResult, Nothing>()

    data class Valid<out ValidResult>(
        val result: ValidResult,
    ) : SecretHitlerUpdateValidationResult<Nothing, ValidResult>()
}

inline fun <reified E : SecretHitlerEphemeralState, R, CheckValid> SecretHitlerGameList.updateRunningGameWithValidation(
    id: SecretHitlerGameId,
    onNoSuchGame: () -> R,
    onInvalidType: () -> R,
    crossinline checkCustomError: (game: SecretHitlerGameState.Running.With<E>) -> SecretHitlerUpdateValidationResult<R, CheckValid>, // Returns non-null if no change should be made. Otherwise, validMapper is applied and the state is updated.
    crossinline validMapper: (validGame: SecretHitlerGameState.Running.With<E>, checkResult: CheckValid) -> Pair<SecretHitlerGameState, R>,
): R {
    return updateRunningGameWithValidExtract(
        id = id,
        onNoSuchGame = onNoSuchGame,
        onInvalidType = onInvalidType,
        validMapper = { currentState: SecretHitlerGameState.Running.With<E> ->
            when (val customCheckResult = checkCustomError(currentState)) {
                is SecretHitlerUpdateValidationResult.Invalid -> {
                    currentState to customCheckResult.result
                }

                is SecretHitlerUpdateValidationResult.Valid -> {
                    validMapper(currentState, customCheckResult.result)
                }
            }
        },
        afterValid = { it },
    )
}

interface SecretHitlerChannelGameMap {
    fun gameByChannelId(channelId: String): SecretHitlerGameId?
    fun channelIdByGame(gameId: SecretHitlerGameId): String?

    /**
     * Attempts to set the game id for [channelId] to [gameId], failing if the channel already has a game id set, or
     * if the game id is already associated with a channel.
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
