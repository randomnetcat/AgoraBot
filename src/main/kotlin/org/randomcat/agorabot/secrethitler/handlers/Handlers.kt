package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState

object SecretHitlerHandlers {
    fun sendJoinLeaveMessage(
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

    fun handleStart(
        context: SecretHitlerCommandContext,
        gameList: SecretHitlerGameList,
        gameId: SecretHitlerGameId,
    ) {
        doHandleSecretHitlerStart(
            context = context,
            gameList = gameList,
            gameId = gameId,
        )
    }
}
