package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState

private typealias CommandReceiver = BaseCommandExecutionReceiverGuilded

object SecretHitlerHandlers {
    private fun CommandReceiver.context() = SecretHitlerCommandContext(this)

    fun CommandReceiver.sendJoinLeaveMessage(
        gameId: SecretHitlerGameId,
        state: SecretHitlerGameState.Joining,
    ) {
        doSendSecretHitlerJoinLeaveMessage(
            context = context(),
            gameId = gameId,
            state = state,
        )
    }

    fun CommandReceiver.handleStart(
        repository: SecretHitlerRepository,
        gameId: SecretHitlerGameId,
    ) {
        doHandleSecretHitlerStart(
            context = context(),
            gameList = repository.gameList,
            gameId = gameId,
        )
    }
}
