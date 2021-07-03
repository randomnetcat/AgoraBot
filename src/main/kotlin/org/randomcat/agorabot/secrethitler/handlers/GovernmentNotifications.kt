package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.util.MAX_BUTTONS_PER_ROW
import java.time.Duration

private val PRESIDENT_POLICY_CHOICE_EXPIRY = Duration.ofDays(1)

private fun sendPresidentPolicySelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    val presidentNumber = currentState.ephemeralState.governmentMembers.president
    val policyKinds = currentState.ephemeralState.options.policies

    context.sendPrivateMessage(
        recipient = currentState.globalState.playerMap.playerByNumberKnown(presidentNumber),
        gameId = gameId,
        message = MessageBuilder()
            .setEmbed(
                EmbedBuilder()
                    .appendDescription("Please select a policy to discard")
                    .also { builder ->
                        policyKinds.forEachIndexed { index, policyType ->
                            val humanIndex = index + 1

                            builder.addField(
                                "Policy #$humanIndex",
                                when (policyType) {
                                    SecretHitlerPolicyType.LIBERAL -> "Liberal"
                                    SecretHitlerPolicyType.FASCIST -> "Fascist"
                                },
                                false,
                            )
                        }
                    }
                    .build()
            )
            .also { builder ->
                val buttons = policyKinds.mapIndexed { index, _ ->
                    val humanIndex = index + 1

                    Button.primary(
                        context.newButtonId(
                            descriptor = SecretHitlerPresidentPolicyChoiceButtonDescriptor(
                                gameId = gameId,
                                president = presidentNumber,
                                policyIndex = index,
                            ),
                            expiryDuration = PRESIDENT_POLICY_CHOICE_EXPIRY,
                        ),
                        "Discard policy #$humanIndex",
                    )
                }

                builder.setActionRows(buttons.chunked(MAX_BUTTONS_PER_ROW) { ActionRow.of(it) })
            }
            .build()
    )
}

private fun sendElectedNotification(
    context: SecretHitlerGameContext,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    fun renderNameForNumber(number: SecretHitlerPlayerNumber): String {
        return context.renderExternalName(currentState.globalState.playerMap.playerByNumberKnown(number))
    }

    context.sendGameMessage(
        MessageBuilder()
            .setEmbed(
                EmbedBuilder()
                    .setTitle("Government Elected")
                    .appendDescription("The government has been elected. Policy selection will now commence.")
                    .addField(
                        "President",
                        renderNameForNumber(currentState.ephemeralState.governmentMembers.president),
                        true,
                    )
                    .addField(
                        "Chancellor",
                        renderNameForNumber(currentState.ephemeralState.governmentMembers.chancellor),
                        true,
                    )
                    .build(),
            )
            .build(),
    )
}

fun sendSecretHitlerGovernmentElectedMessages(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    sendElectedNotification(context, currentState)

    sendPresidentPolicySelectionMessage(
        context = context,
        currentState = currentState,
        gameId = gameId,
    )
}
