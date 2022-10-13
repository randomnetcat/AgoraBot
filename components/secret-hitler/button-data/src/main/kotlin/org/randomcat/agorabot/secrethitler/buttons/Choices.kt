package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

@Serializable
data class SecretHitlerChancellorCandidateSelectionButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val selectedChancellor: SecretHitlerPlayerNumber,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerVoteButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val voteKind: SecretHitlerEphemeralState.VoteKind,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerPresidentPolicyChoiceButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val policyIndex: Int,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerChancellorPolicyChoiceButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val policyIndex: Int,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerChancellorRequestVetoButtonDescriptor(
    override val gameId: SecretHitlerGameId,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerPresidentAcceptVetoButtonDescriptor(
    override val gameId: SecretHitlerGameId,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerPresidentRejectVetoButtonDescriptor(
    override val gameId: SecretHitlerGameId,
) : SecretHitlerButtonRequestDescriptor
