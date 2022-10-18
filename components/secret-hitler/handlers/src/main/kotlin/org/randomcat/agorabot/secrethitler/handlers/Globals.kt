package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.model.SecretHitlerRole
import java.security.SecureRandom
import java.time.Duration
import java.util.*

object SecretHitlerGlobals {
    private val threadRng = ThreadLocal.withInitial { SecureRandom() }

    private fun randomSource(): Random {
        return threadRng.get()
    }

    fun shuffleProvider(): SecretHitlerDeckState.ShuffleProvider {
        return SecretHitlerDeckState.RandomShuffleProvider { randomSource() }
    }

    fun shufflePlayerOrder(players: List<SecretHitlerPlayerExternalName>): List<SecretHitlerPlayerExternalName> {
        return players.shuffled(randomSource())
    }

    fun shuffleRoles(roles: List<SecretHitlerRole>): List<SecretHitlerRole> {
        return roles.shuffled(randomSource())
    }
}

internal val SECRET_HITLER_BUTTON_EXPIRY = Duration.ofDays(7)