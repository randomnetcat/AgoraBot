package org.randomcat.agorabot.secrethitler

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.util.DiscordMessage

fun formatSecretHitlerJoinMessageEmbed(state: SecretHitlerGameState.Joining): MessageEmbed {
    return EmbedBuilder()
        .setTitle("Secret Hitler Game")
        .addField(
            "Players",
            state.playerNames.joinToString("\n") { name -> "<@${name.raw}>" }.ifEmpty { "[None yet]" },
            false,
        )
        .build()
}

fun formatSecretHitlerJoinMessage(
    state: SecretHitlerGameState.Joining,
    joinButtonId: ButtonRequestId,
    leaveButtonId: ButtonRequestId,
): DiscordMessage {
    return MessageBuilder(formatSecretHitlerJoinMessageEmbed(state = state))
        .setActionRows(
            ActionRow.of(
                Button.success(joinButtonId.raw, "Join"),
                Button.danger(leaveButtonId.raw, "Leave"),
            ),
        )
        .build()
}
