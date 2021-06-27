package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.buttons.BUTTON_INVALID_ID_RAW
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import java.time.Duration

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

fun secretHitlerSendChancellorSelectionMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    state: SecretHitlerGameState,
) {
    require(state is SecretHitlerGameState.Running)
    require(state.ephemeralState is SecretHitlerEphemeralState.ChancellorSelectionPending)

    val presidentCandidate = state.ephemeralState.presidentCandidate
    val presidentCandidateName = state.globalState.playerMap.playerByNumber(presidentCandidate)

    val sortedPlayerNumbers = state.globalState.playerMap.validNumbers.sortedBy { it.raw }

    val buttons = sortedPlayerNumbers.mapIndexed { index, chancellorCandidate ->
        val permissible = state.chancellorSelectionIsValid(
            presidentCandidate = presidentCandidate,
            chancellorCandidate = chancellorCandidate,
        )

        Button.primary(
            if (permissible)
                context.newButtonId(
                    SecretHitlerChancellorCandidateSelectionButtonDescriptor(
                        gameId = gameId,
                        president = presidentCandidate,
                        selectedChancellor = chancellorCandidate,
                    ),
                    Duration.ofDays(1),
                )
            else
                BUTTON_INVALID_ID_RAW,
            "Candidate #${index + 1}"
        ).let {
            if (permissible) it else it.asDisabled()
        }
    }

    val actionRows = buttons.chunked(5).map { ActionRow.of(it) }

    val embed = EmbedBuilder()
        .setTitle("${context.renderExternalName(presidentCandidateName)}, please pick a Chancellor")
        .addField(
            "Candidates",
            sortedPlayerNumbers.withIndex().joinToString("\n") { (index, playerNumber) ->
                "Candidate #${index + 1}: " +
                        context.renderExternalName(state.globalState.playerMap.playerByNumber(playerNumber))
            },
            false,
        )

    val message = MessageBuilder(embed).setActionRows(actionRows).build()
    context.sendGameMessage(message)
}
