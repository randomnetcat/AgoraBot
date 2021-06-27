package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerVoteButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState
import org.randomcat.agorabot.secrethitler.updateGameTypedWithValidExtract
import org.randomcat.agorabot.util.handleTextResponse
import java.time.Duration

private fun formatVotingEmbed(
    context: SecretHitlerNameContext,
    currentState: SecretHitlerGameState.Running,
): MessageEmbed {
    require(currentState.ephemeralState is SecretHitlerEphemeralState.VotingOngoing)

    val playerMap = currentState.globalState.playerMap
    val governmentMembers = currentState.ephemeralState.governmentMembers

    val presidentName = playerMap.playerByNumber(governmentMembers.president)
    val chancellorName = playerMap.playerByNumber(governmentMembers.chancellor)

    return EmbedBuilder()
        .addField(
            "President",
            context.renderExternalName(presidentName),
            true,
        )
        .addField(
            "Chancellor",
            context.renderExternalName(chancellorName),
            true,
        )
        .addField(
            "Voting Players",
            currentState
                .ephemeralState
                .voteMap
                .votingPlayers
                .sortedBy { it.raw }
                .joinToString("\n") { votingPlayerNumber ->
                    context.renderExternalName(playerMap.playerByNumber(votingPlayerNumber))
                }
                .ifEmpty { "[None yet]" },
            false,
        )
        .build()
}

private val VOTE_BUTTON_EXPIRY = Duration.ofDays(1)

private fun formatVotingMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running,
): Message {
    require(currentState.ephemeralState is SecretHitlerEphemeralState.VotingOngoing)

    val embed = formatVotingEmbed(context, currentState)

    return MessageBuilder(embed)
        .setActionRows(
            ActionRow.of(
                Button.success(
                    context.newButtonId(
                        descriptor = SecretHitlerVoteButtonDescriptor(
                            gameId = gameId,
                            voteKind = SecretHitlerEphemeralState.VoteKind.FOR,
                        ),
                        expiryDuration = VOTE_BUTTON_EXPIRY,
                    ),
                    "FOR",
                ),
                Button.danger(
                    context.newButtonId(
                        descriptor = SecretHitlerVoteButtonDescriptor(
                            gameId = gameId,
                            voteKind = SecretHitlerEphemeralState.VoteKind.AGAINST,
                        ),
                        expiryDuration = VOTE_BUTTON_EXPIRY,
                    ),
                    "AGAINST",
                )
            )
        )
        .build()
}

internal fun doSendSecretHitlerVotingMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    gameState: SecretHitlerGameState.Running,
) {
    require(gameState.ephemeralState is SecretHitlerEphemeralState.VotingOngoing)
    context.sendGameMessage(formatVotingMessage(context, gameId, gameState))
}

private sealed class VoteButtonResult {
    data class Success(val newState: SecretHitlerGameState.Running) : VoteButtonResult()

    sealed class Failure : VoteButtonResult()
    object InvalidType : Failure()
    object AlreadyVoted : Failure()
}

private const val INVALID_TYPE_MESSAGE = "You can no longer vote in that game."

internal fun doHandleSecretHitlerVote(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonClickEvent,
    request: SecretHitlerVoteButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId

        val response = repository.gameList.updateGameTypedWithValidExtract(
            id = gameId,
            onNoSuchGame = {
                "That game no longer exists."
            },
            onInvalidType = { _ ->
                INVALID_TYPE_MESSAGE
            },
            validMapper = { currentState: SecretHitlerGameState.Running ->
                val voterName = context.nameFromInteraction(event.interaction)
                val voterNumber = currentState.globalState.playerMap.numberByPlayer(voterName)

                if (currentState.ephemeralState !is SecretHitlerEphemeralState.VotingOngoing) {
                    return@updateGameTypedWithValidExtract currentState to VoteButtonResult.InvalidType
                }

                if (currentState.ephemeralState.voteMap.votingPlayers.contains(voterNumber)) {
                    return@updateGameTypedWithValidExtract currentState to VoteButtonResult.AlreadyVoted
                }

                val newState = currentState.copy(
                    ephemeralState = currentState.ephemeralState.copy(
                        voteMap = currentState.ephemeralState.voteMap.withVote(voterNumber, request.voteKind),
                    )
                )

                newState to VoteButtonResult.Success(newState = newState)
            },
            afterValid = { result ->
                when (result) {
                    is VoteButtonResult.Success -> {
                        "Vote cast."
                    }

                    is VoteButtonResult.AlreadyVoted -> {
                        "You have already voted."
                    }

                    is VoteButtonResult.InvalidType -> {
                        INVALID_TYPE_MESSAGE
                    }
                }
            }
        )

        response
    }
}
