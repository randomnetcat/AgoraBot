package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId

@Serializable
data class SecretHitlerJoinGameButtonDescriptor(
    override val gameId: SecretHitlerGameId,
) : SecretHitlerButtonRequestDescriptor

@Serializable
data class SecretHitlerLeaveGameButtonDescriptor(
    override val gameId: SecretHitlerGameId,
) : SecretHitlerButtonRequestDescriptor
