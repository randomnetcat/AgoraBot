package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerWinResult

fun sendSecretHitlerWinMessage(
    context: SecretHitlerGameContext,
    winResult: SecretHitlerWinResult,
) {
    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle(
                    "Game Over",
                )
                .appendDescription(
                    when (winResult) {
                        is SecretHitlerWinResult.LiberalsWin -> "Liberals win."
                        is SecretHitlerWinResult.FascistsWin -> "Fascists win."
                    },
                )
                .build(),
        ).build(),
    )
}
