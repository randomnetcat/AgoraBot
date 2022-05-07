package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.secrethitler.context.SecretHitlerGameContext
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerWinResult

private val SecretHitlerWinResult.winMessage: String
    get() = when (this) {
        SecretHitlerWinResult.FascistsWin.FascistPolicyGoalReached -> {
            "The Fascist policy goal has been reached."
        }

        SecretHitlerWinResult.FascistsWin.HitlerElectedChancellor -> {
            "Hitler has been elected chancellor after a sufficient number of Fascist policies were enacted."
        }

        SecretHitlerWinResult.LiberalsWin.HitlerKilled -> {
            "Hitler has been killed."
        }

        SecretHitlerWinResult.LiberalsWin.LiberalPolicyGoalReached -> {
            "The Liberal policy goal has been reached."
        }
    }

suspend fun sendSecretHitlerWinMessage(
    context: SecretHitlerGameContext,
    winResult: SecretHitlerWinResult,
) {
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle(
                    "Game Over",
                )
                .setDescription(winResult.winMessage)
                .addField(
                    "Result",
                    when (winResult) {
                        is SecretHitlerWinResult.LiberalsWin -> "Liberals win."
                        is SecretHitlerWinResult.FascistsWin -> "Fascists win."
                    },
                    true,
                )
                .build(),
        ).build(),
    )
}
