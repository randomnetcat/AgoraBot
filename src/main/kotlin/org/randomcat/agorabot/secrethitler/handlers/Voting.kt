package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.randomcat.agorabot.secrethitler.SecretHitlerGameList
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerVoteButtonDescriptor
import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerAfterVoteResult
import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerInactiveGovernmentResult
import org.randomcat.agorabot.secrethitler.model.transitions.afterNewVote
import org.randomcat.agorabot.secrethitler.updateRunningGameWithValidation
import org.randomcat.agorabot.util.handleTextResponse
import java.time.Duration
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Invalid as InvalidResult
import org.randomcat.agorabot.secrethitler.SecretHitlerUpdateValidationResult.Valid as ValidResult

private fun formatVotingEmbed(
    context: SecretHitlerNameContext,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
): MessageEmbed {
    val playerMap = currentState.globalState.playerMap
    val governmentMembers = currentState.ephemeralState.governmentMembers

    val presidentName = playerMap.playerByNumberKnown(governmentMembers.president)
    val chancellorName = playerMap.playerByNumberKnown(governmentMembers.chancellor)

    return EmbedBuilder()
        .setTitle("Voting on Government")
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
                    context.renderExternalName(playerMap.playerByNumberKnown(votingPlayerNumber))
                }
                .ifEmpty { "[None yet]" },
            false,
        )
        .addField(currentState.globalState.liberalPoliciesEmbedField())
        .addField(currentState.globalState.fascistPoliciesEmbedField())
        .addField(
            "Cards in Draw Deck",
            currentState.globalState.boardState.deckState.drawDeck.policyCount.toString(),
            true,
        )
        .addField(currentState.globalState.electionTrackerEmbedField())
        .addField(
            "Next Fascist Power",
            run {
                val fascistPoliciesEnacted = currentState.globalState.boardState.policiesState.fascistPoliciesEnacted

                if (fascistPoliciesEnacted == currentState.globalState.configuration.fascistWinRequirement - 1) {
                    "[Win]"
                } else {
                    val power =
                        currentState
                            .globalState
                            .configuration
                            .fascistPowerAt(fascistPoliciesEnacted + 1)

                    power?.readableName ?: "[None]"
                }
            },
            true,
        )
        .build()
}

private val VOTE_BUTTON_EXPIRY = Duration.ofDays(1)

private fun formatVotingMessage(
    context: SecretHitlerGameContext,
    gameId: SecretHitlerGameId,
    currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
): Message {
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
    gameState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
) {
    context.sendGameMessage(formatVotingMessage(context, gameId, gameState))
}

private sealed class VoteButtonResult {
    data class Success(
        val originalState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
        val nestedResult: SecretHitlerAfterVoteResult,
    ) : VoteButtonResult()

    sealed class Failure : VoteButtonResult()
    object NoSuchGame : Failure()
    object InvalidType : Failure()
    object AlreadyVoted : Failure()
    object NotPlayer : Failure()
}

private suspend fun queueVoteMessageUpdate(
    context: SecretHitlerGameContext,
    targetMessage: Message,
    gameList: SecretHitlerGameList,
    gameId: SecretHitlerGameId,
    stateAfterUpdate: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>,
) {
    context.enqueueEditGameMessage(targetMessage) {
        // Don't update if the state has changed out from under us.
        val currentState = gameList.gameById(gameId)

        if (currentState == stateAfterUpdate) {
            MessageBuilder(targetMessage)
                .setEmbeds(
                    formatVotingEmbed(
                        context = context,
                        currentState = stateAfterUpdate,
                    ),
                )
                .build()
        } else {
            null
        }
    }
}

private fun SecretHitlerInactiveGovernmentResult.stateForUpdate(): SecretHitlerGameState {
    return when (this) {
        is SecretHitlerInactiveGovernmentResult.NewElection -> this.newState
        is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameContinues -> this.newState
        is SecretHitlerInactiveGovernmentResult.CountryInChaos.GameEnds -> SecretHitlerGameState.Completed
    }
}

private fun SecretHitlerAfterVoteResult.stateForUpdate(): SecretHitlerGameState {
    return when (this) {
        is SecretHitlerAfterVoteResult.VotingContinues -> this.newState
        is SecretHitlerAfterVoteResult.GovernmentElected.GameContinues -> this.newState
        is SecretHitlerAfterVoteResult.GovernmentElected.GameEnds -> SecretHitlerGameState.Completed
        is SecretHitlerAfterVoteResult.GovernmentRejected -> this.nestedResult.stateForUpdate()
    }
}

private data class VoteCheckResult(val voterNumber: SecretHitlerPlayerNumber)

private fun updateState(
    gameList: SecretHitlerGameList,
    gameId: SecretHitlerGameId,
    voterName: SecretHitlerPlayerExternalName,
    voteKind: SecretHitlerEphemeralState.VoteKind,
): VoteButtonResult {
    return gameList.updateRunningGameWithValidation(
        id = gameId,
        onNoSuchGame = {
            VoteButtonResult.NoSuchGame
        },
        onInvalidType = {
            VoteButtonResult.InvalidType
        },
        checkCustomError = { currentState ->
            val voterNumber = currentState.globalState.playerMap.numberByPlayer(voterName)

            when {
                voterNumber == null -> {
                    InvalidResult(VoteButtonResult.NotPlayer)
                }

                currentState.ephemeralState.voteMap.votingPlayers.contains(voterNumber) -> {
                    InvalidResult(VoteButtonResult.AlreadyVoted)
                }

                else -> {
                    ValidResult(VoteCheckResult(voterNumber = voterNumber))
                }
            }
        },
        validMapper = { currentState: SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.VotingOngoing>, checkResult ->
            val voterNumber = checkResult.voterNumber

            val voteResult = currentState.afterNewVote(
                voter = voterNumber,
                voteKind = voteKind,
                shuffleProvider = SecretHitlerGlobals.shuffleProvider(),
            )

            val newState = voteResult.stateForUpdate()

            newState to VoteButtonResult.Success(
                originalState = currentState,
                nestedResult = voteResult,
            )
        },
    )
}

private fun sendVoteSummaryMessage(
    context: SecretHitlerGameContext,
    playerMap: SecretHitlerPlayerMap,
    voteMap: SecretHitlerEphemeralState.VoteMap,
) {
    fun renderPlayerByNumber(number: SecretHitlerPlayerNumber): String {
        return context.renderExternalName(playerMap.playerByNumberKnown(number))
    }

    fun renderVoteListField(players: Set<SecretHitlerPlayerNumber>): String {
        return players.joinToString("\n") { renderPlayerByNumber(it) }.ifEmpty { "None" }
    }

    context.sendGameMessage(
        MessageBuilder(
            EmbedBuilder()
                .setTitle("Voting Complete")
                .addField(
                    "For",
                    renderVoteListField(voteMap.playersVotingFor()),
                    true,
                )
                .addField(
                    "Against",
                    renderVoteListField(voteMap.playersVotingAgainst()),
                    true,
                )
                .build()

        ).build(),
    )
}

internal suspend fun doHandleSecretHitlerVote(
    repository: SecretHitlerRepository,
    context: SecretHitlerInteractionContext,
    event: ButtonInteractionEvent,
    request: SecretHitlerVoteButtonDescriptor,
) {
    handleTextResponse(event) {
        val gameId = request.gameId

        val updateResult = updateState(
            gameList = repository.gameList,
            gameId = gameId,
            voterName = context.nameFromInteraction(event.interaction),
            voteKind = request.voteKind,
        )

        when (updateResult) {
            is VoteButtonResult.Success -> {
                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (val afterVoteResult = updateResult.nestedResult) {
                    is SecretHitlerAfterVoteResult.VotingContinues -> {
                        queueVoteMessageUpdate(
                            context = context,
                            targetMessage = checkNotNull(event.message),
                            gameList = repository.gameList,
                            gameId = gameId,
                            stateAfterUpdate = afterVoteResult.newState,
                        )
                    }

                    is SecretHitlerAfterVoteResult.VotingComplete -> {
                        val playerMap = updateResult.originalState.globalState.playerMap
                        val governmentMembers = updateResult.originalState.ephemeralState.governmentMembers

                        sendVoteSummaryMessage(
                            context = context,
                            playerMap = playerMap,
                            voteMap = afterVoteResult.completeVoteMap,
                        )

                        when (afterVoteResult) {
                            is SecretHitlerAfterVoteResult.GovernmentElected -> {
                                sendSecretHitlerGovernmentElectedNotification(
                                    context = context,
                                    playerMap = playerMap,
                                    governmentMembers = governmentMembers,
                                )

                                when (afterVoteResult) {
                                    is SecretHitlerAfterVoteResult.GovernmentElected.GameContinues -> {
                                        check(afterVoteResult.newState.globalState.playerMap == playerMap)
                                        check(afterVoteResult.newState.ephemeralState.governmentMembers == governmentMembers)

                                        sendSecretHitlerPresidentPolicySelectionMessage(
                                            context = context,
                                            currentState = afterVoteResult.newState,
                                            gameId = gameId,
                                        )
                                    }

                                    is SecretHitlerAfterVoteResult.GovernmentElected.GameEnds -> {
                                        sendSecretHitlerWinMessage(
                                            context = context,
                                            winResult = afterVoteResult.winResult,
                                        )
                                    }
                                }
                            }

                            is SecretHitlerAfterVoteResult.GovernmentRejected -> {
                                sendSecretHitlerGovernmentRejectedMessages(
                                    context = context,
                                    gameId = gameId,
                                    playerMap = playerMap,
                                    governmentMembers = governmentMembers,
                                    result = afterVoteResult.nestedResult,
                                )
                            }
                        }
                    }
                }

                "Vote cast."
            }

            is VoteButtonResult.NoSuchGame -> {
                "That game no longer exists."
            }

            is VoteButtonResult.AlreadyVoted -> {
                "You have already voted."
            }

            is VoteButtonResult.InvalidType -> {
                "You can no longer vote in that game."
            }

            is VoteButtonResult.NotPlayer -> {
                "You are not a player in that game."
            }
        }
    }
}
