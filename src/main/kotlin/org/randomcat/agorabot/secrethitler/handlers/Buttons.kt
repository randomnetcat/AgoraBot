package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.*
import org.randomcat.agorabot.secrethitler.context.SecretHitlerInteractionContext
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentExecutePowerSelection
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentInvestigatePowerSelection
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentSpecialElectionPowerSelection

object SecretHitlerButtons {
    suspend fun handleJoin(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerJoinGameButtonDescriptor,
    ) {
        doHandleSecretHitlerJoin(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handleLeave(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerLeaveGameButtonDescriptor,
    ) {
        doHandleSecretHitlerLeave(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handleChancellorSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerChancellorSelect(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handleVote(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerVoteButtonDescriptor,
    ) {
        doHandleSecretHitlerVote(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentPolicySelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPresidentPolicyChoiceButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentPolicySelected(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handleChancellorPolicySelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerChancellorPolicyChoiceButtonDescriptor,
    ) {
        doHandleSecretHitlerChancellorPolicySelected(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentInvestigatePowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPendingInvestigatePartySelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentInvestigatePowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentSpecialElectionPowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPendingSpecialElectionSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentSpecialElectionPowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentExecutePowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPendingExecutionSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentExecutePowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handleChancellorVetoRequest(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerChancellorRequestVetoButtonDescriptor,
    ) {
        doHandleSecretHitlerChancellorVetoRequest(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentVetoApproval(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPresidentAcceptVetoButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentVetoApproval(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    suspend fun handlePresidentVetoRejection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonInteractionEvent,
        request: SecretHitlerPresidentRejectVetoButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentVetoRejection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }
}
