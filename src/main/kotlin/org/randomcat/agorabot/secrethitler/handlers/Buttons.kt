package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.commands.SecretHitlerCommand
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository

object SecretHitlerButtons {
    fun handleJoin(
        repository: SecretHitlerRepository,
        nameContext: SecretHitlerNameContext,
        event: ButtonClickEvent,
        request: SecretHitlerCommand.JoinGameRequestDescriptor,
    ) {
        doHandleSecretHitlerJoin(
            repository = repository,
            context = nameContext,
            event = event,
            request = request,
        )
    }

    fun handleLeave(
        repository: SecretHitlerRepository,
        nameContext: SecretHitlerNameContext,
        event: ButtonClickEvent,
        request: SecretHitlerCommand.LeaveGameRequestDescriptor,
    ) {
        doHandleSecretHitlerLeave(
            repository = repository,
            context = nameContext,
            event = event,
            request = request,
        )
    }
}
