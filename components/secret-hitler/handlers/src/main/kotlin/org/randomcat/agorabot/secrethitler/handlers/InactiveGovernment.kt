package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.secrethitler.context.SecretHitlerGameContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.readableName
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerInactiveGovernmentResult

suspend fun sendSecretHitlerInactiveGovernmentMessages(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    result: SecretHitlerInactiveGovernmentResult,
) {
    return when (result) {
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
