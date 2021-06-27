package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerJoinGameButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerLeaveGameButtonDescriptor

object SecretHitlerButtons {
    fun handleJoin(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerJoinGameButtonDescriptor,
    ) {
        doHandleSecretHitlerJoin(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handleLeave(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerLeaveGameButtonDescriptor,
    ) {
        doHandleSecretHitlerLeave(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handleChancellorSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerChancellorSelect(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }
}
