package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerAfterChancellorPolicySelectedResult
import org.randomcat.agorabot.secrethitler.model.transitions.afterChancellorPolicySelected
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

private sealed class ChancellorPolicySelectedHandlerUpdateResult {
    data class Success(
        val nestedResult: SecretHitlerAfterChancellorPolicySelectedResult,
        val originalState: GameState.Running.With<EphemeralState.ChancellorPolicyChoicePending>,
    ) : ChancellorPolicySelectedHandlerUpdateResult()

    sealed class Failure : ChancellorPolicySelectedHandlerUpdateResult()
    object NoSuchGame : Failure()
    object NotPlayer : Failure()
    object Unauthorized : Failure()
    object InvalidState : Failure()
}

private fun doStateUpdate(
    repository: SecretHitlerRepository,
    gameId: SecretHitlerGameId,
    actualChancellorName: SecretHitlerPlayerExternalName,
    selectedPolicyIndex: Int,
): ChancellorPolicySelectedHandlerUpdateResult {
    return repository.gameList.updateRunningGameWithValidExtract(
        id = gameId,
        onNoSuchGame = {
            ChancellorPolicySelectedHandlerUpdateResult.NoSuchGame
        },
        onInvalidType = {
            ChancellorPolicySelectedHandlerUpdateResult.InvalidState
        },
        validMapper = { currentState: GameState.Running.With<EphemeralState.ChancellorPolicyChoicePending> ->
            val actualChancellorNumber = currentState.globalState.playerMap.numberByPlayer(actualChancellorName)
            if (actualChancellorNumber == null) {
                return@updateRunningGameWithValidExtract currentState to ChancellorPolicySelectedHandlerUpdateResult.NotPlayer
            }

            val expectedChancellorNumber = currentState.ephemeralState.governmentMembers.chancellor

            if (actualChancellorNumber != expectedChancellorNumber) {
                return@updateRunningGameWithValidExtract currentState to ChancellorPolicySelectedHandlerUpdateResult.Unauthorized
            }

            val nestedResult = currentState.afterChancellorPolicySelected(policyIndex = selectedPolicyIndex)

            val newState = when (nestedResult) {
                is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues -> {
                    nestedResult.newState
                }

                is SecretHitlerAfterChancellorPolicySelectedResult.GameEnds -> {
                    GameState.Completed
                }
            }

            newState to ChancellorPolicySelectedHandlerUpdateResult.Success(
                nestedResult = nestedResult,
                originalState = currentState,
            )
        },
        afterValid = { result ->
            result
        },
    )
}

private fun sendPolicyEnactedNotification(
    context: SecretHitlerGameContext,
    policyType: SecretHitlerPolicyType,
    power: SecretHitlerFascistPower?,
) {
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle("Policy enacted")
                .addField(
                    "Policy kind",
                    policyType.readableName,
                    true
                )
                .addField(
                    "Power activated",
                    power?.readableName ?: "[None]",
                    true,
                )
                .build()
        ).build()
    )
}

private fun ChancellorPolicySelectedHandlerUpdateResult.Success.effectivePower(): SecretHitlerFascistPower? {
    return when (nestedResult) {
        is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.WithPower -> {
            nestedResult.power
        }

        is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.NoPower -> {
            null
        }
        is SecretHitlerAfterChancellorPolicySelectedResult.GameEnds -> {
            null
        }
    }
}


fun doHandleSecretHitlerChancellorPolicySelected(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerChancellorPolicyChoiceButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId

        val updateResult = doStateUpdate(
            repository = repository,
            gameId = gameId,
            actualChancellorName = context.nameFromInteraction(event.interaction),
            selectedPolicyIndex = request.policyIndex,
        )

        when (updateResult) {
            is ChancellorPolicySelectedHandlerUpdateResult.Success -> {
                sendPolicyEnactedNotification(
                    context = context,
                    policyType = updateResult.nestedResult.policyType,
                    power = updateResult.effectivePower(),
                )

                fun sendElectionStartMessage(
                    newState: GameState.Running.With<EphemeralState.ChancellorSelectionPending>,
                ) {
                    secretHitlerSendChancellorSelectionMessage(
                        context = context,
                        gameId = gameId,
                        state = newState,
                    )
                }

                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (updateResult.nestedResult) {
                    is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.NoPower -> {
                        sendElectionStartMessage(newState = updateResult.nestedResult.newState)
                    }

                    is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.WithPower.Stateful -> {
                        sendSecretHitlerPowerActivatedMessages(
                            context = context,
                            gameId = gameId,
                            currentState = updateResult.nestedResult.newState,
                        )
                    }

                    is SecretHitlerAfterChancellorPolicySelectedResult.GameContinues.WithPower.Stateless -> {
                        val ensureExhaustive = when (updateResult.nestedResult.powerResult) {
                            SecretHitlerAfterChancellorPolicySelectedResult.StatelessPowerKind.PolicyPeek -> {
                                sendPolicyPeekMessages(
                                    context = context,
                                    gameId = gameId,
                                    playerMap = updateResult.originalState.globalState.playerMap,
                                    enactingGovernment = updateResult.originalState.ephemeralState.governmentMembers,
                                    deckState = updateResult.originalState.globalState.boardState.deckState,
                                )
                            }
                        }

                        sendElectionStartMessage(newState = updateResult.nestedResult.newState)
                    }

                    is SecretHitlerAfterChancellorPolicySelectedResult.GameEnds -> {
                        sendSecretHitlerWinMessage(
                            context = context,
                            winResult = updateResult.nestedResult.winResult,
                        )
                    }
                }

                "That policy has been enacted."
            }

            is ChancellorPolicySelectedHandlerUpdateResult.Unauthorized -> {
                "You are not the Chancellor in that game."
            }

            is ChancellorPolicySelectedHandlerUpdateResult.NotPlayer -> {
                "You are not a player in that game."
            }

            is ChancellorPolicySelectedHandlerUpdateResult.InvalidState -> {
                "You can no longer select a policy in that game."
            }

            is ChancellorPolicySelectedHandlerUpdateResult.NoSuchGame -> {
                "That game no longer exists."
            }
        }
    }
}

private fun sendPolicyPeekMessages(
    context: SecretHitlerInteractionContext,
    gameId: SecretHitlerGameId,
    playerMap: SecretHitlerPlayerMap,
    enactingGovernment: SecretHitlerGovernmentMembers,
    deckState: SecretHitlerDeckState,
) {
    val peekResult = deckState.peekStandard()

    val presidentName = playerMap.playerByNumberKnown(enactingGovernment.president)

    context.sendPrivateMessage(
        recipient = presidentName,
        gameId = gameId,
        message = MessageBuilder(
            EmbedBuilder()
                .setTitle("Policy Peek")
                .setDescription(
                    peekResult
                        .peekedCards
                        .mapIndexed { index, policyType ->
                            "Policy #${index + 1}: ${policyType.readableName}"
                        }
                        .joinToString("\n"),
                )
                .build(),
        ).build(),
    )

    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle("Policy Peek")
                .setDescription(
                    "${context.renderExternalName(presidentName)} has been shown the top ${peekResult.peekedCards.size} policies."
                )
                .build(),
        ).build(),
    )
}
