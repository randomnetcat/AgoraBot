package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.*
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState.PresidentPolicyOptions
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

sealed class SecretHitlerAfterVoteResult {
    data class VotingContinues(
        val newState: GameState.Running.With<EphemeralState.VotingOngoing>,
    ) : SecretHitlerAfterVoteResult()

    sealed class VotingComplete : SecretHitlerAfterVoteResult()

    data class GovernmentElected(
        val newState: GameState.Running.With<EphemeralState.PresidentPolicyChoicePending>,
        val shuffledDeck: Boolean,
    ) : VotingComplete()

    data class GovernmentRejected(
        val nestedResult: SecretHitlerInactiveGovernmentResult,
    ) : VotingComplete()
}

private fun SecretHitlerGlobalGameState.withDeckState(
    newDeckState: SecretHitlerDeckState,
): SecretHitlerGlobalGameState {
    return this.copy(
        boardState = this.boardState.copy(
            deckState = newDeckState,
        )
    )
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
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerAfterVoteResult.GovernmentElected {
    val drawResult = this.globalState.boardState.deckState.drawStandard(shuffleProvider)

    val newGlobalState =
        this
            .globalState
            .withDeckState(drawResult.newDeck)
            .withTermLimitedGovernment(this.ephemeralState.governmentMembers)

    val newEphemeralState = EphemeralState.PresidentPolicyChoicePending(
        governmentMembers = this.ephemeralState.governmentMembers,
        options = PresidentPolicyOptions(drawResult.drawnCards),
    )

    return SecretHitlerAfterVoteResult.GovernmentElected(
        newState = GameState.Running(newGlobalState, newEphemeralState),
        shuffledDeck = drawResult.shuffled,
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
            afterElectedGovernment(shuffleProvider)
        } else {
            SecretHitlerAfterVoteResult.GovernmentRejected(afterInactiveGovernment(shuffleProvider = shuffleProvider))
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
