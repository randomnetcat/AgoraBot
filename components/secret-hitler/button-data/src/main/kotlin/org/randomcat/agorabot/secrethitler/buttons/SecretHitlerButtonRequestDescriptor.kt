package org.randomcat.agorabot.secrethitler.buttons

import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId

interface SecretHitlerButtonRequestDescriptor : ButtonRequestDescriptor {
    val gameId: SecretHitlerGameId
}