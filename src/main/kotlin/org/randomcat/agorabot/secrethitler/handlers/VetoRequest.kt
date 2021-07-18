package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorRequestVetoButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentAcceptVetoButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentRejectVetoButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidation
import org.randomcat.agorabot.util.handleTextResponse
import java.time.Duration
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Invalid as InvalidResult
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Valid as ValidResult

private sealed class VetoRequestResult {
    data class Success(
        val newState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>,
    ) : VetoRequestResult()

    sealed class Failure : VetoRequestResult()

    object NoSuchGame : Failure()
    object NotPlayer : Failure()
    object Unauthorized : Failure()
    object InvalidState : Failure()

    object VetoAlreadyRequested : Failure()
    object VetoAlreadyRejected : Failure()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualChancellorName: SecretHitlerPlayerExternalName,
): VetoRequestResult {
    return repository.gameList.updateRunningGameWithValidation(
        id = gameId,
        onNoSuchGame = { VetoRequestResult.NoSuchGame },
        onInvalidType = { VetoRequestResult.InvalidState },
        checkCustomError = { currentState ->
            val actualChancellorNumber = currentState.globalState.playerMap.numberByPlayer(actualChancellorName)
            val expectedChancellorNumber = currentState.ephemeralState.governmentMembers.chancellor

            when {
                actualChancellorNumber == null -> {
                    InvalidResult(VetoRequestResult.NotPlayer)
                }

                actualChancellorNumber != expectedChancellorNumber -> {
                    InvalidResult(VetoRequestResult.Unauthorized)
                }

                else -> {
                    when (currentState.ephemeralState.vetoState) {
                        SecretHitlerEphemeralState.VetoRequestState.REQUESTED -> {
                            InvalidResult(VetoRequestResult.VetoAlreadyRequested)
                        }

                        SecretHitlerEphemeralState.VetoRequestState.REJECTED -> {
                            InvalidResult(VetoRequestResult.VetoAlreadyRejected)
                        }

                        SecretHitlerEphemeralState.VetoRequestState.NOT_REQUESTED -> {
                            ValidResult(Unit)
                        }
                    }
                }
            }
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>, _ ->
            val newState = currentState.withEphemeral(
                currentState.ephemeralState.copy(
                    vetoState = SecretHitlerEphemeralState.VetoRequestState.REQUESTED,
                ),
            )

            newState to VetoRequestResult.Success(newState)
        },
    )
}

private val BUTTON_EXPIRY = Duration.ofDays(1)

private fun sendVetoRequestNotification(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    presidentName: SecretHitlerPlayerExternalName,
    chancellorName: SecretHitlerPlayerExternalName,
) {
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle("Veto Request")
                .setDescription("The Chancellor has requested to veto the proposed policies. The President may approve or deny this request.")
                .addField(
                    "President",
                    context.renderExternalName(presidentName),
                    true,
                )
                .addField(
                    "Chancellor",
                    context.renderExternalName(chancellorName),
                    true,
                )
                .build(),
        )
            .setActionRows(
                ActionRow.of(
                    Button.success(
                        context.newButtonId(
                            descriptor = SecretHitlerPresidentAcceptVetoButtonDescriptor(
                                gameId = gameId,
                            ),
                            expiryDuration = BUTTON_EXPIRY,
                        ),
                        "Approve Veto",
                    ),
                    Button.danger(
                        context.newButtonId(
                            descriptor = SecretHitlerPresidentRejectVetoButtonDescriptor(
                                gameId = gameId,
                            ),
                            expiryDuration = BUTTON_EXPIRY,
                        ),
                        "Reject Veto",
                    ),
                ),
            )
            .build(),
    )
}

fun doHandleSecretHitlerChancellorVetoRequest(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerChancellorRequestVetoButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId

        val updateResult = doStateUpdate(
            repository = repository,
            gameId = gameId,
            actualChancellorName = context.nameFromInteraction(event.interaction),
        )

        when (updateResult) {
            is VetoRequestResult.Success -> {
                val newState = updateResult.newState

                sendVetoRequestNotification(
                    context = context,
                    gameId = gameId,
                    presidentName = newState.globalState.playerMap.playerByNumberKnown(
                        newState.ephemeralState.governmentMembers.president,
                    ),
                    chancellorName = newState.globalState.playerMap.playerByNumberKnown(
                        newState.ephemeralState.governmentMembers.chancellor,
                    ),
                )

                "The President has been asked to approve or reject this veto. " +
                        "You may still choose to enact a policy before they do so."
            }

            VetoRequestResult.NoSuchGame -> {
                "That game no longer exists."
            }

            VetoRequestResult.InvalidState -> {
                "You can no longer request a veto in that game."
            }

            VetoRequestResult.NotPlayer -> {
                "You are not a player in that game."
            }

            VetoRequestResult.Unauthorized -> {
                "You are not the Chancellor in that game."
            }

            VetoRequestResult.VetoAlreadyRequested -> {
                "You have already requested a veto in that game."
            }

            VetoRequestResult.VetoAlreadyRejected -> {
                "The President has already rejected a veto in that game."
            }
        }
    }
}
