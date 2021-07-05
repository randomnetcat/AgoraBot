package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerInactiveGovernmentResult

private fun sendBasicElectionNotification(
    context: SecretHitlerGameContext,
    title: String,
    description: String,
    playerMap: SecretHitlerPlayerMap,
    governmentMembers: SecretHitlerGovernmentMembers,
) {
    fun renderNameForNumber(number: SecretHitlerPlayerNumber): String {
        return context.renderExternalName(playerMap.playerByNumberKnown(number))
    }

    context.sendGameMessage(
        MessageBuilder()
            .setEmbed(
                EmbedBuilder()
                    .setTitle(title)
                    .appendDescription(description)
                    .addField(
                        "President",
                        renderNameForNumber(governmentMembers.president),
                        true,
                    )
                    .addField(
                        "Chancellor",
                        renderNameForNumber(governmentMembers.chancellor),
                        true,
                    )
                    .build(),
            )
            .build(),
    )
}

private fun sendElectedNotification(
    context: SecretHitlerGameContext,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    sendBasicElectionNotification(
        context = context,
        title = "Government Elected",
        description = "The government has been elected. Policy selection will now commence.",
        playerMap = currentState.globalState.playerMap,
        governmentMembers = currentState.ephemeralState.governmentMembers,
    )
}

fun sendSecretHitlerGovernmentElectedMessages(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    sendElectedNotification(context, currentState)

    sendSecretHitlerPresidentPolicySelectionMessage(
        context = context,
        currentState = currentState,
        gameId = gameId,
    )
}


private fun sendGovernmentRejectedNotification(
    context: SecretHitlerGameContext,
    playerMap: SecretHitlerPlayerMap,
    governmentMembers: SecretHitlerGovernmentMembers,
) {
    sendBasicElectionNotification(
        context = context,
        title = "Government Rejected",
        description = "The government has been rejected. The election tracker has been incremented by 1.",
        playerMap = playerMap,
        governmentMembers = governmentMembers,
    )
}

fun sendSecretHitlerGovernmentRejectedMessages(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    playerMap: SecretHitlerPlayerMap,
    governmentMembers: SecretHitlerGovernmentMembers,
    result: SecretHitlerInactiveGovernmentResult,
) {
    sendGovernmentRejectedNotification(
        context = context,
        playerMap = playerMap,
        governmentMembers = governmentMembers,
    )

    @Suppress("UNUSED_VARIABLE")
    val ensureExhaustive = when (result) {
        is SecretHitlerInactiveGovernmentResult.NewElection -> {
            secretHitlerSendChancellorSelectionMessage(
                context = context,
                gameId = gameId,
                state = result.newState,
            )
        }

        is SecretHitlerInactiveGovernmentResult.CountryInChaos -> {
            context.sendGameMessage(
                MessageBuilder(
                    EmbedBuilder()
                        .setTitle("Country in Chaos")
                        .appendDescription("The election tracker has reached the maximum value. The top policy on the deck has been enacted.")
                        .addField(
                            "Policy Enacted",
                            result.drawnPolicyType.readableName,
                            false,
                        )
                        .build(),
                ).build()
            )

            when (result) {
                is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameContinues -> {
                    secretHitlerSendChancellorSelectionMessage(
                        context = context,
                        gameId = gameId,
                        state = result.newState,
                    )
                }

                is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameEnds -> {
                    sendSecretHitlerWinMessage(
                        context = context,
                        winResult = result.winResult,
                    )
                }
            }
        }
    }
}
