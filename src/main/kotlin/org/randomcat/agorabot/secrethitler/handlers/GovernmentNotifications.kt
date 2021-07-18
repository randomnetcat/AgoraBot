package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGovernmentMembers
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerMap
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
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

fun sendSecretHitlerGovernmentElectedNotification(
    context: SecretHitlerGameContext,
    playerMap: SecretHitlerPlayerMap,
    governmentMembers: SecretHitlerGovernmentMembers,
) {
    sendBasicElectionNotification(
        context = context,
        title = "Government Elected",
        description = "The government has been elected.",
        playerMap = playerMap,
        governmentMembers = governmentMembers,
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

    sendSecretHitlerInactiveGovernmentMessages(
        context = context,
        gameId = gameId,
        result = result,
    )
}
