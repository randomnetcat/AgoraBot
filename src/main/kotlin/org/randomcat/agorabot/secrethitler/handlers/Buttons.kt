package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.*
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentExecutePowerSelection
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentInvestigatePowerSelection
import org.randomcat.agorabot.secrethitler.handlers.power_selections.doHandleSecretHitlerPresidentSpecialElectionPowerSelection

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

    fun handleVote(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerVoteButtonDescriptor,
    ) {
        doHandleSecretHitlerVote(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handlePresidentPolicySelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerPresidentPolicyChoiceButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentPolicySelected(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handleChancellorPolicySelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerChancellorPolicyChoiceButtonDescriptor,
    ) {
        doHandleSecretHitlerChancellorPolicySelected(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handlePresidentInvestigatePowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerPendingInvestigatePartySelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentInvestigatePowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handlePresidentSpecialElectionPowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerPendingSpecialElectionSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentSpecialElectionPowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }

    fun handlePresidentExecutePowerSelection(
        repository: SecretHitlerRepository,
        context: SecretHitlerInteractionContext,
        event: ButtonClickEvent,
        request: SecretHitlerPendingExecutionSelectionButtonDescriptor,
    ) {
        doHandleSecretHitlerPresidentExecutePowerSelection(
            repository = repository,
            context = context,
            event = event,
            request = request,
        )
    }
}
