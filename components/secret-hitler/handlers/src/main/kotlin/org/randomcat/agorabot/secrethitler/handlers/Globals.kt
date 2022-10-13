package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import java.time.Duration

object SecretHitlerGlobals {
    fun shuffleProvider(): SecretHitlerDeckState.ShuffleProvider = SecretHitlerDeckState.RandomShuffleProvider
}

internal val SECRET_HITLER_BUTTON_EXPIRY = Duration.ofDays(7)