package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

@Serializable
data class SecretHitlerPendingInvestigatePartySelectionButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val president: SecretHitlerPlayerNumber,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : ButtonRequestDescriptor

@Serializable
data class SecretHitlerPendingSpecialElectionSelectionButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val president: SecretHitlerPlayerNumber,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : ButtonRequestDescriptor

@Serializable
data class SecretHitlerPendingExecutionSelectionButtonDescriptor(
    val gameId: SecretHitlerGameId,
    val president: SecretHitlerPlayerNumber,
    val selectedPlayer: SecretHitlerPlayerNumber,
) : ButtonRequestDescriptor
