package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import java.time.Duration

object SecretHitlerGlobals {
    fun shuffleProvider(): SecretHitlerDeckState.ShuffleProvider = SecretHitlerDeckState.RandomShuffleProvider

    fun shufflePlayerOrder(players: List<SecretHitlerPlayerExternalName>): List<SecretHitlerPlayerExternalName> {
        return players.shuffled()
    }
}

internal val SECRET_HITLER_BUTTON_EXPIRY = Duration.ofDays(7)