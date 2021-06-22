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

    data class GovernmentMembers(
        val president: SecretHitlerPlayerNumber,
        val chancellor: SecretHitlerPlayerNumber,
    )

    data class ChancellorSelectionPending(
        val presidentCandidate: SecretHitlerPlayerNumber,
    ) : SecretHitlerEphemeralState()

    data class VotingOngoing(val governmentMembers: GovernmentMembers) : SecretHitlerEphemeralState()

    data class PresidentPolicyChoicePending(
        val governmentMembers: GovernmentMembers,
        val options: PresidentPolicyOptions,
    ) : SecretHitlerEphemeralState()

    data class ChancellorPolicyChoicePending(
        val governmentMembers: GovernmentMembers,
        val options: ChancellorPolicyOptions,
        val vetoState: VetoRequestState,
    ) : SecretHitlerEphemeralState()
}
