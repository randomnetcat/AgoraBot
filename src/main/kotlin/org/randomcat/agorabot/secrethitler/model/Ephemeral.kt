package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.*

sealed class SecretHitlerEphemeralState {
    data class PresidentPolicyOptions(val policies: ImmutableList<SecretHitlerPolicyType>) {
        constructor(policies: List<SecretHitlerPolicyType>) : this(policies.toImmutableList())

        init {
            require(policies.size == 3)
        }
    }

    data class ChancellorPolicyOptions(val policies: ImmutableList<SecretHitlerPolicyType>) {
        constructor(policies: List<SecretHitlerPolicyType>) : this(policies.toImmutableList())

        init {
            require(policies.size == 2)
        }
    }

    enum class VetoRequestState {
        NOT_REQUESTED,
        REQUESTED,
        REJECTED,
    }

    data class ChancellorSelectionPending(
        val presidentCandidate: SecretHitlerPlayerNumber,
    ) : SecretHitlerEphemeralState() {
        fun selectChancellor(chancellor: SecretHitlerPlayerNumber): VotingOngoing {
            return VotingOngoing(
                governmentMembers = SecretHitlerGovernmentMembers(
                    president = presidentCandidate,
                    chancellor = chancellor,
                ),
                voteMap = VoteMap.empty(),
            )
        }
    }

    enum class VoteKind {
        FOR,
        AGAINST,
    }

    data class VoteMap(private val votesByPlayer: ImmutableMap<SecretHitlerPlayerNumber, VoteKind>) {
        constructor(votesByPlayer: Map<SecretHitlerPlayerNumber, VoteKind>) : this(votesByPlayer.toImmutableMap())

        companion object {
            private val EMPTY = VoteMap(persistentMapOf())
            fun empty(): VoteMap = EMPTY
        }

        val votingPlayers: Set<SecretHitlerPlayerNumber> = votesByPlayer.keys

        fun voteForPlayer(playerNumber: SecretHitlerPlayerNumber): VoteKind {
            return votesByPlayer.getValue(playerNumber)
        }

        fun withVote(player: SecretHitlerPlayerNumber, vote: VoteKind): VoteMap {
            require(!votesByPlayer.containsKey(player)) {
                "Attempt to set duplicate vote for player with name ${player.raw}"
            }

            return VoteMap(votesByPlayer.toPersistentMap().put(player, vote))
        }

        fun toMap(): Map<SecretHitlerPlayerNumber, VoteKind> {
            return votesByPlayer
        }
    }

    data class VotingOngoing(
        val governmentMembers: SecretHitlerGovernmentMembers,
        val voteMap: VoteMap,
    ) : SecretHitlerEphemeralState()

    data class PresidentPolicyChoicePending(
        val governmentMembers: SecretHitlerGovernmentMembers,
        val options: PresidentPolicyOptions,
    ) : SecretHitlerEphemeralState()

    data class ChancellorPolicyChoicePending(
        val governmentMembers: SecretHitlerGovernmentMembers,
        val options: ChancellorPolicyOptions,
        val vetoState: VetoRequestState,
    ) : SecretHitlerEphemeralState()
}
