package org.randomcat.agorabot.secrethitler.buttons

import kotlinx.serialization.Serializable
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId

@Serializable
data class SecretHitlerJoinGameButtonDescriptor(val gameId: SecretHitlerGameId) : ButtonRequestDescriptor

@Serializable
data class SecretHitlerLeaveGameButtonDescriptor(val gameId: SecretHitlerGameId) : ButtonRequestDescriptor
