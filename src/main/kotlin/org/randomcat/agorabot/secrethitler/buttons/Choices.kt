package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

@Serializable
data class SecretHitlerChancellorCandidateSelectionButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val president: SecretHitlerPlayerNumber,
    val selectedChancellor: SecretHitlerPlayerNumber,
) : ButtonRequestDescriptor

@Serializable
data class SecretHitlerVoteButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val voteKind: SecretHitlerEphemeralState.VoteKind,
) : ButtonRequestDescriptor

@Serializable
data class SecretHitlerPresidentPolicyChoiceButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val president: SecretHitlerPlayerNumber,
    val policyIndex: Int,
) : ButtonRequestDescriptor
