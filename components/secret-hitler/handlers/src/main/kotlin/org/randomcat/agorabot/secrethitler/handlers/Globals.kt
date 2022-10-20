package org.randomcat.agorabot.secrethitler.handlers

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerExternalName
import org.randomcat.agorabot.secrethitler.model.SecretHitlerRole
import org.randomcat.agorabot.util.userFacingRandom
import java.time.Duration

object SecretHitlerGlobals {
    fun shuffleProvider(): SecretHitlerDeckState.ShuffleProvider {
        return SecretHitlerDeckState.RandomShuffleProvider { userFacingRandom() }
    }

    fun shufflePlayerOrder(players: List<SecretHitlerPlayerExternalName>): List<SecretHitlerPlayerExternalName> {
        return players.shuffled(userFacingRandom())
    }

    fun shuffleRoles(roles: List<SecretHitlerRole>): List<SecretHitlerRole> {
        return roles.shuffled(userFacingRandom())
    }
}

internal val SECRET_HITLER_BUTTON_EXPIRY = Duration.ofDays(7)