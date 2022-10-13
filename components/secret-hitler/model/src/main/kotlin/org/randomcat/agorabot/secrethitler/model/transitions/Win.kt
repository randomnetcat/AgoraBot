package org.randomcat.agorabot.secrethitler.model.transitions

sealed class SecretHitlerWinResult {
    sealed class LiberalsWin : SecretHitlerWinResult() {
        object HitlerKilled : LiberalsWin()
        object LiberalPolicyGoalReached : LiberalsWin()
    }

    sealed class FascistsWin : SecretHitlerWinResult() {
        object HitlerElectedChancellor : FascistsWin()
        object FascistPolicyGoalReached : FascistsWin()
    }
}
