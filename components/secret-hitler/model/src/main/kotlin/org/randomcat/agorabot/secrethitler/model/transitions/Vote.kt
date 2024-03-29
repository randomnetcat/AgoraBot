package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState.PresidentPolicyOptions
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

sealed class SecretHitlerAfterVoteResult {
    data class VotingContinues(
        val newState: GameState.Running.With<EphemeralState.VotingOngoing>,
    ) : SecretHitlerAfterVoteResult()

    sealed class VotingComplete : SecretHitlerAfterVoteResult() {
        abstract val completeVoteMap: EphemeralState.VoteMap
    }

    sealed class GovernmentElected : VotingComplete() {
        data class GameContinues(
            val newState: GameState.Running.With<EphemeralState.PresidentPolicyChoicePending>,
            override val completeVoteMap: EphemeralState.VoteMap,
        ) : GovernmentElected()

        data class GameEnds(
            val winResult: SecretHitlerWinResult,
            override val completeVoteMap: EphemeralState.VoteMap,
        ) : GovernmentElected()
    }

    data class GovernmentRejected(
        val nestedResult: SecretHitlerInactiveGovernmentResult,
        override val completeVoteMap: EphemeralState.VoteMap,
    ) : VotingComplete()
}

private fun SecretHitlerGlobalGameState.withTermLimitedGovernment(
    termLimitedGovernment: SecretHitlerGovernmentMembers,
): SecretHitlerGlobalGameState {
    return this.copy(
        electionState = this.electionState.copy(
            termLimitState = SecretHitlerTermLimitState(
                termLimitedGovernment = termLimitedGovernment,
            ),
        ),
    )
}

private fun GameState.Running.With<EphemeralState.VotingOngoing>.afterElectedGovernment(
    completeVoteMap: EphemeralState.VoteMap,
): SecretHitlerAfterVoteResult.GovernmentElected {
    if (
        globalState.roleMap.roleOf(ephemeralState.governmentMembers.chancellor) is SecretHitlerRole.Hitler &&
        globalState.boardState.policiesState.fascistPoliciesEnacted >= globalState.configuration.hitlerChancellorWinRequirement
    ) {
        return SecretHitlerAfterVoteResult.GovernmentElected.GameEnds(
            winResult = SecretHitlerWinResult.FascistsWin.HitlerElectedChancellor,
            completeVoteMap = completeVoteMap,
        )
    }

    val drawResult = this.globalState.boardState.deckState.drawDeck.drawStandard()

    val newGlobalState =
        this
            .globalState
            .withDeckState(
                SecretHitlerDeckState(
                    drawDeck = drawResult.newDeck,
                    discardDeck = this.globalState.boardState.deckState.discardDeck,
                ),
            )
            .withTermLimitedGovernment(this.ephemeralState.governmentMembers)

    val newEphemeralState = EphemeralState.PresidentPolicyChoicePending(
        governmentMembers = this.ephemeralState.governmentMembers,
        options = PresidentPolicyOptions(drawResult.drawnCards),
    )

    return SecretHitlerAfterVoteResult.GovernmentElected.GameContinues(
        newState = GameState.Running(newGlobalState, newEphemeralState),
        completeVoteMap = completeVoteMap,
    )
}

fun GameState.Running.With<EphemeralState.VotingOngoing>.afterNewVote(
    voter: SecretHitlerPlayerNumber,
    voteKind: EphemeralState.VoteKind,
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerAfterVoteResult {
    require(!ephemeralState.voteMap.votingPlayers.contains(voter))

    val newVoteState = ephemeralState.voteMap.withVote(
        player = voter,
        vote = voteKind
    )

    return if (newVoteState.votingPlayers == globalState.playerMap.validNumbers) {
        val forCount = newVoteState.playersVotingFor().count()
        val againstCount = newVoteState.playersVotingAgainst().count()

        if (forCount > againstCount) {
            afterElectedGovernment(
                completeVoteMap = newVoteState,
            )
        } else {
            SecretHitlerAfterVoteResult.GovernmentRejected(
                nestedResult = globalState.afterInactiveGovernment(shuffleProvider = shuffleProvider),
                completeVoteMap = newVoteState,
            )
        }
    } else {
        SecretHitlerAfterVoteResult.VotingContinues(
            newState = this.withEphemeral(
                newEphemeralState = this.ephemeralState.copy(
                    voteMap = newVoteState,
                )
            )
        )
    }
}
