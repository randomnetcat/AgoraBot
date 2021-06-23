package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
                SecretHitlerGovernmentMembers(
                    president = presidentCandidate,
                    chancellor = chancellor,
                )
            )
        }
    }

    data class VotingOngoing(val governmentMembers: SecretHitlerGovernmentMembers) : SecretHitlerEphemeralState()

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
