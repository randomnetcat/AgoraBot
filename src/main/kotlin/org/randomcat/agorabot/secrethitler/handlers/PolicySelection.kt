package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorPolicyChoiceButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorRequestVetoButtonDescriptor
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
    allowVeto: Boolean,
) {
    context.sendPrivateMessage(
        recipient = recipientName,
        gameId = gameId,
        message = MessageBuilder()
            .setEmbeds(
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
                val policyButtons = policyKinds.mapIndexed { index, _ ->
                    val humanIndex = index + 1

                    Button.primary(
                        context.newButtonId(
                            descriptor = makeButtonDescriptor(index),
                            expiryDuration = PRESIDENT_POLICY_CHOICE_EXPIRY,
                        ),
                        "$selectVerb policy #$humanIndex",
                    )
                }

                val allButtons = if (allowVeto) {
                    policyButtons + Button.danger(
                        context.newButtonId(
                            descriptor = SecretHitlerChancellorRequestVetoButtonDescriptor(
                                gameId = gameId,
                            ),
                            PRESIDENT_POLICY_CHOICE_EXPIRY,
                        ),
                        "Request Veto",
                    )
                } else {
                    policyButtons
                }

                builder.setActionRows(allButtons.chunked(MAX_BUTTONS_PER_ROW) { ActionRow.of(it) })
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
        allowVeto = false,
    )
}

private val SecretHitlerGlobalGameState.chancellorCanVeto
    get() = boardState.policiesState.fascistPoliciesEnacted >= configuration.vetoUnlockRequirement

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
        allowVeto = state.globalState.chancellorCanVeto && state.ephemeralState.vetoState == SecretHitlerEphemeralState.VetoRequestState.NOT_REQUESTED,
    )
}
