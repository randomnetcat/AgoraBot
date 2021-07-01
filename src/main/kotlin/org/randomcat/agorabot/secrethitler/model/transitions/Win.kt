package org.randomcat.agorabot.secrethitler.model.transitions

sealed class SecretHitlerWinResult {
    object LiberalsWin : SecretHitlerWinResult()
    object FascistsWin : SecretHitlerWinResult()
}
