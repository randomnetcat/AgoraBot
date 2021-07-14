package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPresidentPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.util.MAX_BUTTONS_PER_ROW
import java.time.Duration

private val PRESIDENT_POLICY_CHOICE_EXPIRY = Duration.ofDays(1)

private inline fun sendPolicySelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    description: String,
    selectVerb: String,
    recipientName: SecretHitlerPlayerExternalName,
    policyKinds: List<SecretHitlerPolicyType>,
    makeButtonDescriptor: (policyIndex: Int) -> ButtonRequestDescriptor,
) {
    context.sendPrivateMessage(
        recipient = recipientName,
        gameId = gameId,
        message = MessageBuilder()
            .setEmbed(
                EmbedBuilder()
                    .appendDescription(description)
                    .also { builder ->
                        policyKinds.forEachIndexed { index, policyType ->
                            val humanIndex = index + 1

                            builder.addField(
                                "Policy #$humanIndex",
                                policyType.readableName,
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
                            descriptor = makeButtonDescriptor(index),
                            expiryDuration = PRESIDENT_POLICY_CHOICE_EXPIRY,
                        ),
                        "$selectVerb policy #$humanIndex",
                    )
                }

                builder.setActionRows(buttons.chunked(MAX_BUTTONS_PER_ROW) { ActionRow.of(it) })
            }
            .build()
    )
}

fun sendSecretHitlerPresidentPolicySelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PresidentPolicyChoicePending>,
) {
    val presidentNumber = currentState.ephemeralState.governmentMembers.president
    val policyKinds = currentState.ephemeralState.options.policies

    sendPolicySelectionMessage(
        context = context,
        gameId = gameId,
        description = "Please select a policy to discard.",
        selectVerb = "Discard",
        recipientName = currentState.globalState.playerMap.playerByNumberKnown(presidentNumber),
        policyKinds = policyKinds,
        makeButtonDescriptor = { policyIndex ->
            SecretHitlerPresidentPolicyChoiceButtonDescriptor(
                gameId = gameId,
                policyIndex = policyIndex,
            )
        },
    )
}

fun sendSecretHitlerChancellorPolicySelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    state: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>,
) {
    val chancellorNumber = state.ephemeralState.governmentMembers.chancellor

    sendPolicySelectionMessage(
        context = context,
        gameId = gameId,
        description = "Please select a policy to enact.",
        selectVerb = "Enact",
        recipientName = state.globalState.playerMap.playerByNumberKnown(chancellorNumber),
        policyKinds = state.ephemeralState.options.policies,
        makeButtonDescriptor = { policyIndex ->
            SecretHitlerChancellorPolicyChoiceButtonDescriptor(
                gameId = gameId,
                policyIndex = policyIndex,
            )
        },
    )
}
