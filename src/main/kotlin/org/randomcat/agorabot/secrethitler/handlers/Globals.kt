package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState

object SecretHitlerGlobals {
    fun shuffleProvider(): SecretHitlerDeckState.ShuffleProvider = SecretHitlerDeckState.RandomShuffleProvider
}
