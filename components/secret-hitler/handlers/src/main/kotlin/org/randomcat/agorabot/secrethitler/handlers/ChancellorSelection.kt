package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.context.SecretHitlerGameContext
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.util.MAX_BUTTONS_PER_ROW

private const val LAST_PRESIDENT_INELIGIBILITY_THRESHOLD = 5

fun SecretHitlerGameState.Running.chancellorSelectionIsValid(
    presidentCandidate: SecretHitlerPlayerNumber,
    chancellorCandidate: SecretHitlerPlayerNumber,
): Boolean {
    if (presidentCandidate == chancellorCandidate) {
        return false
    }

    val termLimitedGovernment = globalState.electionState.termLimitState.termLimitedGovernment ?: return true

    if (
        globalState.playerMap.playerCount > LAST_PRESIDENT_INELIGIBILITY_THRESHOLD &&
        termLimitedGovernment.president == chancellorCandidate
    ) {
        return false
    }

    if (termLimitedGovernment.chancellor == chancellorCandidate) {
        return false
    }

    return true
}

suspend fun secretHitlerSendChancellorSelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    state: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorSelectionPending>,
) {
    val presidentCandidate = state.ephemeralState.presidentCandidate
    val presidentCandidateName = state.globalState.playerMap.playerByNumberKnown(presidentCandidate)

    val sortedPlayerNumbers = state.globalState.playerMap.validNumbers.sortedBy { it.raw }

    val buttons = sortedPlayerNumbers.mapIndexed { index, chancellorCandidate ->
        val permissible = state.chancellorSelectionIsValid(
            presidentCandidate = presidentCandidate,
            chancellorCandidate = chancellorCandidate,
        )

        Button
            .primary(
                if (permissible)
                    context.newButtonId(
                        SecretHitlerChancellorCandidateSelectionButtonDescriptor(
                            gameId = gameId,
                            selectedChancellor = chancellorCandidate,
                        ),
                        SECRET_HITLER_BUTTON_EXPIRY,
                    )
                else
                    context.invalidButtonId(),
                "Candidate #${index + 1}"
            )
            .withDisabled(!permissible)
    }

    val actionRows = buttons.chunked(MAX_BUTTONS_PER_ROW) { ActionRow.of(it) }

    val embed = EmbedBuilder()
        .setTitle("Presidential Candidate, please pick a Chancellor")
        .addField(
            "Presidential Candidate",
            context.renderExternalName(presidentCandidateName),
            true,
        )
        .addField(
            "Candidates",
            sortedPlayerNumbers.withIndex().joinToString("\n") { (index, playerNumber) ->
                "Candidate #${index + 1}: " +
                        context.renderExternalName(state.globalState.playerMap.playerByNumberKnown(playerNumber))
            },
            false,
        )

    val message = MessageBuilder(embed).setActionRows(actionRows).build()
    context.sendGameMessage(message)
}
