package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

@Serializable
data class SecretHitlerPendingInvestigatePartySelectionButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerPendingSpecialElectionSelectionButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerPendingExecutionSelectionButtonDescriptor(
    override val gameId: SecretHitlerGameId,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : SecretHitlerButtonRequestDescriptor
