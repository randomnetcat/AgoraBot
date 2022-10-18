package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.context.SecretHitlerCommandContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerGameList

object SecretHitlerHandlers {
    suspend fun sendJoinLeaveMessage(
        context: SecretHitlerCommandContext,
        gameId: SecretHitlerGameId,
        state: SecretHitlerGameState.Joining,
    ) {
        doSendSecretHitlerJoinLeaveMessage(
            context = context,
            gameId = gameId,
            state = state,
        )
    }

    suspend fun handleStart(
        context: SecretHitlerCommandContext,
        gameList: SecretHitlerGameList,
        gameId: SecretHitlerGameId,
        shuffleRoles: SecretHitlerShuffleRoles,
    ) {
        doHandleSecretHitlerStart(
            context = context,
            gameList = gameList,
            gameId = gameId,
            shuffleRoles = shuffleRoles,
        )
    }
}
