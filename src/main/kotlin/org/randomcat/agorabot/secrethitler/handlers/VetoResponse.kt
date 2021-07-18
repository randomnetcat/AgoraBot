package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentAcceptVetoButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentRejectVetoButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerInactiveGovernmentResult
import org.randomcat.agorabot.secrethitler.model.transitions.afterVetoApproved
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidation
import org.randomcat.agorabot.util.handleTextResponse
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Invalid as InvalidResult
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Valid as ValidResult

private sealed class VetoResponseCommonFailure(val standardErrorMessage: String) {
    object NoSuchGame : VetoResponseCommonFailure("That game no longer exists.")
    object NotPlayer : VetoResponseCommonFailure("You are not a player in that game.")
    object Unauthorized : VetoResponseCommonFailure("You are not the President in that game.")
    object InvalidState : VetoResponseCommonFailure("You can no longer respond to a veto in that game.")

    object VetoNotRequested : VetoResponseCommonFailure("A veto has not been requested in that game.")
    object VetoAlreadyHandled : VetoResponseCommonFailure("You have already responded to a veto in that game.")
}

private inline fun <R> doGenericStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
    crossinline mapFailure: (VetoResponseCommonFailure) -> R,
    crossinline validMapper: (SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>) -> Pair<SecretHitlerGameState, R>,
): R {
    return repository.gameList.updateRunningGameWithValidation(
        id = gameId,
        onNoSuchGame = { mapFailure(VetoResponseCommonFailure.NoSuchGame) },
        onInvalidType = { mapFailure(VetoResponseCommonFailure.InvalidState) },
        checkCustomError = { currentState ->
            val actualPresidentNumber = currentState.globalState.playerMap.numberByPlayer(actualPresidentName)
            val expectedPresidentNumber = currentState.ephemeralState.governmentMembers.president

            when {
                actualPresidentNumber == null -> {
                    InvalidResult(mapFailure(VetoResponseCommonFailure.NotPlayer))
                }

                actualPresidentNumber != expectedPresidentNumber -> {
                    InvalidResult(mapFailure(VetoResponseCommonFailure.Unauthorized))
                }

                else -> {
                    when (currentState.ephemeralState.vetoState) {
                        SecretHitlerEphemeralState.VetoRequestState.NOT_REQUESTED -> {
                            InvalidResult(mapFailure(VetoResponseCommonFailure.VetoNotRequested))
                        }

                        SecretHitlerEphemeralState.VetoRequestState.REJECTED -> {
                            InvalidResult(mapFailure(VetoResponseCommonFailure.VetoAlreadyHandled))
                        }

                        SecretHitlerEphemeralState.VetoRequestState.REQUESTED -> {
                            ValidResult(Unit)
                        }
                    }
                }
            }
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>, _ ->
            validMapper(currentState)
        },
    )
}

private sealed class VetoApprovalResult {
    data class Success(val nestedResult: SecretHitlerInactiveGovernmentResult) : VetoApprovalResult()
    data class Failure(val failureReason: VetoResponseCommonFailure) : VetoApprovalResult()
}

private fun doApprovalStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
): VetoApprovalResult {
    return doGenericStateUpdate(
        repository = repository,
        gameId = gameId,
        actualPresidentName = actualPresidentName,
        mapFailure = VetoApprovalResult::Failure,
        validMapper = { currentState ->
            val nestedResult = currentState.afterVetoApproved(
                shuffleProvider = SecretHitlerGlobals.shuffleProvider(),
            )

            val effectiveNewState = when (nestedResult) {
                is SecretHitlerInactiveGovernmentResult.NewElection -> nestedResult.newState
                is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameContinues -> nestedResult.newState
                is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameEnds -> SecretHitlerGameState.Completed
            }

            effectiveNewState to VetoApprovalResult.Success(nestedResult = nestedResult)
        },
    )
}

private fun sendGenericVetoNotification(
    context: SecretHitlerGameContext,
    presidentName: SecretHitlerPlayerExternalName,
    title: String,
    description: String,
) {
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .addField(
                    "President",
                    context.renderExternalName(presidentName),
                    true,
                )
                .build()
        ).build(),
    )
}

private fun sendVetoApprovedNotification(
    context: SecretHitlerGameContext,
    presidentName: SecretHitlerPlayerExternalName,
) {
    sendGenericVetoNotification(
        context = context,
        presidentName = presidentName,
        title = "Veto Approved",
        description = "The President has approved the veto. The election tracker has been incremented by one.",
    )
}

fun doHandleSecretHitlerPresidentVetoApproval(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerPresidentAcceptVetoButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId
        val actualPresidentName = context.nameFromInteraction(event.interaction)

        val updateResult = doApprovalStateUpdate(
            repository = repository,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
        )

        when (updateResult) {
            is VetoApprovalResult.Success -> {
                sendVetoApprovedNotification(
                    context = context,
                    presidentName = actualPresidentName,
                )

                sendSecretHitlerInactiveGovernmentMessages(
                    context = context,
                    gameId = gameId,
                    result = updateResult.nestedResult,
                )

                "The policy options have been vetoed."
            }

            is VetoApprovalResult.Failure -> {
                updateResult.failureReason.standardErrorMessage
            }
        }
    }
}

private sealed class VetoRejectionResult {
    object Success : VetoRejectionResult()
    data class Failure(val failureReason: VetoResponseCommonFailure) : VetoRejectionResult()
}

private fun doRejectionStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualPresidentName: SecretHitlerPlayerExternalName,
): VetoRejectionResult {
    return doGenericStateUpdate(
        repository = repository,
        gameId = gameId,
        actualPresidentName = actualPresidentName,
        mapFailure = VetoRejectionResult::Failure,
        validMapper = { currentState ->
            val newState = currentState.withEphemeral(
                currentState.ephemeralState.copy(
                    vetoState = SecretHitlerEphemeralState.VetoRequestState.REJECTED,
                )
            )

            newState to VetoRejectionResult.Success
        },
    )
}

private fun sendVetoRejectedNotification(
    context: SecretHitlerGameContext,
    presidentName: SecretHitlerPlayerExternalName,
) {
    sendGenericVetoNotification(
        context = context,
        presidentName = presidentName,
        title = "Veto Rejected",
        description = "The President has rejected the veto. The Chancellor must select a policy to enact.",
    )
}

fun doHandleSecretHitlerPresidentVetoRejection(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerPresidentRejectVetoButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId
        val actualPresidentName = context.nameFromInteraction(event.interaction)

        val updateResult = doRejectionStateUpdate(
            repository = repository,
            gameId = gameId,
            actualPresidentName = actualPresidentName,
        )

        when (updateResult) {
            is VetoRejectionResult.Success -> {
                sendVetoRejectedNotification(
                    context = context,
                    presidentName = actualPresidentName,
                )

                "You have rejected the veto."
            }

            is VetoRejectionResult.Failure -> {
                updateResult.failureReason.standardErrorMessage
            }
        }
    }
}
