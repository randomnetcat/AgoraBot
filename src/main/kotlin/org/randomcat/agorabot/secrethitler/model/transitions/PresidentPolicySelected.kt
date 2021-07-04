package org.randomcat.agorabot.secrethitler.model.transitions

import kotlinx.collections.immutable.toPersistentList
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState as EphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState as GameState

fun GameState.Running.With<EphemeralState.PresidentPolicyChoicePending>.afterPresidentPolicySelected(
    policyIndex: Int,
): GameState.Running.With<EphemeralState.ChancellorPolicyChoicePending> {
    require(policyIndex < ephemeralState.options.policies.size) {
        "Invalid policy index $policyIndex"
    }

    return this.withEphemeral(
        newEphemeralState = EphemeralState.ChancellorPolicyChoicePending(
            governmentMembers = this.ephemeralState.governmentMembers,
            options = EphemeralState.ChancellorPolicyOptions(
                this.ephemeralState.options.policies.toPersistentList().removeAt(policyIndex),
            ),
            vetoState = EphemeralState.VetoRequestState.NOT_REQUESTED,
        )
    )
}
