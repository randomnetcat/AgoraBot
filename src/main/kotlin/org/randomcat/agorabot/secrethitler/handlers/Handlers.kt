package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.commands.impl.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.handlers.SecretHitlerHandlers.CommandReceiver
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId

object SecretHitlerHandlers {
    @Suppress("TOPLEVEL_TYPEALIASES_ONLY") // It'll be fine
    private typealias CommandReceiver = BaseCommandExecutionReceiverGuilded

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
