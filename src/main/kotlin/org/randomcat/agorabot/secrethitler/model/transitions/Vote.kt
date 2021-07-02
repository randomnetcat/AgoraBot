package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState.PresidentPolicyOptions
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

sealed class SecretHitlerWithVoteResult {
    data class VotingContinues(
        val newState: GameState.Running.With<EphemeralState.VotingOngoing>,
    ) : SecretHitlerWithVoteResult()

    sealed class VotingComplete : SecretHitlerWithVoteResult()

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

private fun GameState.Running.With<EphemeralState.VotingOngoing>.afterElectedGovernment(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerWithVoteResult.GovernmentElected {
    val drawResult = this.globalState.boardState.deckState.drawStandard(shuffleProvider)

    val newGlobalState = this.globalState.withDeckState(drawResult.newDeck)

    val newEphemeralState = EphemeralState.PresidentPolicyChoicePending(
        governmentMembers = this.ephemeralState.governmentMembers,
        options = PresidentPolicyOptions(drawResult.drawnCards),
    )

    return SecretHitlerWithVoteResult.GovernmentElected(
        newState = GameState.Running(newGlobalState, newEphemeralState),
        shuffledDeck = drawResult.shuffled,
    )
}

fun GameState.Running.With<EphemeralState.VotingOngoing>.afterNewVote(
    voter: SecretHitlerPlayerNumber,
    voteKind: EphemeralState.VoteKind,
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerWithVoteResult {
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
            SecretHitlerWithVoteResult.GovernmentRejected(afterInactiveGovernment(shuffleProvider = shuffleProvider))
        }
    } else {
        SecretHitlerWithVoteResult.VotingContinues(
            newState = this.withEphemeral(
                newEphemeralState = this.ephemeralState.copy(
                    voteMap = newVoteState,
                )
            )
        )
    }
}
