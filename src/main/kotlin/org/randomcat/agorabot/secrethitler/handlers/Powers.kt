package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.BUTTON_INVALID_ID_RAW
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingExecutionSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingInvestigatePartySelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerPendingSpecialElectionSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.util.MAX_BUTTONS_PER_ROW
import java.time.Duration

private val BUTTON_EXPIRY = Duration.ofDays(1)

private fun sendPlayerSelectPowerNotification(
    context: SecretHitlerGameContext,
    powerName: String,
    description: String,
    selectVerb: String,
    presidentNumber: SecretHitlerPlayerNumber,
    playerMap: SecretHitlerPlayerMap,
    extraExcludedPlayers: Set<SecretHitlerPlayerNumber>,
    makeButtonDescriptor: (selectedPlayer: SecretHitlerPlayerNumber) -> ButtonRequestDescriptor,
) {
    val presidentName = playerMap.playerByNumberKnown(presidentNumber)

    val sortedNumbers = playerMap.validNumbers.sortedBy { it.raw }

    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle(powerName)
                .setDescription(description)
                .addField(
                    "President",
                    context.renderExternalName(presidentName),
                    false,
                )
                .addField(
                    "Options",
                    sortedNumbers
                        .mapIndexed { index, playerNumber ->
                            "Option #${index + 1}: " +
                                    context.renderExternalName(playerMap.playerByNumberKnown(playerNumber))
                        }
                        .joinToString("\n"),
                    false,
                )
                .build(),
        )
            .also { builder ->
                val buttons = sortedNumbers.mapIndexed { index, playerNumber ->
                    val isPermissible = playerNumber != presidentNumber && !extraExcludedPlayers.contains(playerNumber)

                    Button
                        .primary(
                            if (isPermissible) {
                                context.newButtonId(
                                    descriptor = makeButtonDescriptor(
                                        playerNumber,
                                    ),
                                    expiryDuration = BUTTON_EXPIRY,
                                )
                            } else {
                                BUTTON_INVALID_ID_RAW
                            },
                            "$selectVerb player ${index + 1}"
                        )
                        .withDisabled(!isPermissible)
                }

                builder.setActionRows(buttons.chunked(MAX_BUTTONS_PER_ROW) { ActionRow.of(it) })
            }
            .build(),
    )
}

fun sendSecretHitlerPowerActivatedMessages(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.PolicyPending>,
) {
    val presidentNumber = currentState.ephemeralState.presidentNumber

    fun doSendSelectMessage(
        powerName: String,
        powerDescription: String,
        selectVerb: String,
        extraExcludedPlayers: Set<SecretHitlerPlayerNumber> = emptySet(),
        makeButtonDescriptor: (playerNumber: SecretHitlerPlayerNumber) -> ButtonRequestDescriptor,
    ) {
        sendPlayerSelectPowerNotification(
            context = context,
            presidentNumber = presidentNumber,
            playerMap = currentState.globalState.playerMap,
            powerName = powerName,
            description = powerDescription,
            selectVerb = selectVerb,
            extraExcludedPlayers = extraExcludedPlayers,
            makeButtonDescriptor = makeButtonDescriptor,
        )
    }

    return when (currentState.ephemeralState) {
        is SecretHitlerEphemeralState.PolicyPending.InvestigateParty -> {
            doSendSelectMessage(
                powerName = SecretHitlerFascistPower.INVESTIGATE_PARTY.readableName,
                powerDescription = "The President must select a player's party to investigate.",
                selectVerb = "Investigate",
                extraExcludedPlayers = currentState.globalState.powersState.previouslyInvestigatedPlayers,
                makeButtonDescriptor = { selectedPlayer ->
                    SecretHitlerPendingInvestigatePartySelectionButtonDescriptor(
                        gameId = gameId,
                        selectedPlayer = selectedPlayer,
                    )
                },
            )
        }

        is SecretHitlerEphemeralState.PolicyPending.SpecialElection -> {
            doSendSelectMessage(
                powerName = SecretHitlerFascistPower.SPECIAL_ELECTION.readableName,
                powerDescription = "The President must select a Presidential candidate for a Special Election.",
                selectVerb = "Nominate",
                makeButtonDescriptor = { selectedPlayer ->
                    SecretHitlerPendingSpecialElectionSelectionButtonDescriptor(
                        gameId = gameId,
                        selectedPlayer = selectedPlayer,
                    )
                },
            )
        }

        is SecretHitlerEphemeralState.PolicyPending.Execution -> {
            doSendSelectMessage(
                powerName = SecretHitlerFascistPower.EXECUTE_PLAYER.readableName,
                powerDescription = "The President must select a player to kill.",
                selectVerb = "Execute",
                makeButtonDescriptor = { selectedPlayer ->
                    SecretHitlerPendingExecutionSelectionButtonDescriptor(
                        gameId = gameId,
                        selectedPlayer = selectedPlayer,
                    )
                },
            )
        }
    }
}
