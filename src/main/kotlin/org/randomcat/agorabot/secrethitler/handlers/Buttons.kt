package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository

object SecretHitlerButtons {
    fun handleJoin(
        repository: SecretHitlerRepository,
        event: ButtonClickEvent,
        request: SecretHitlerCommand.JoinGameRequestDescriptor,
    ) {
        doHandleSecretHitlerJoin(
            repository = repository,
            event = event,
            request = request,
        )
    }

    fun handleLeave(
        repository: SecretHitlerRepository,
        event: ButtonClickEvent,
        request: SecretHitlerCommand.LeaveGameRequestDescriptor,
    ) {
        doHandleSecretHitlerLeave(
            repository = repository,
            event = event,
            request = request,
        )
    }
}
