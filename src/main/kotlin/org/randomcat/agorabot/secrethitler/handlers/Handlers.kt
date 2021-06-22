package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId

private typealias CommandReceiver = BaseCommandExecutionReceiverGuilded

object SecretHitlerHandlers {
    private fun CommandReceiver.context() = SecretHitlerCommandContext(this)

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
