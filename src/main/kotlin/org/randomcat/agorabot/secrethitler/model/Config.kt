package org.randomcat.agorabot.secrethitler.model

import kotlinx.collections.immutable.ImmutableList

data class SecretHitlerGameConfiguration(
    val liberalWinRequirement: Int,
    private val fascistPowers: ImmutableList<SecretHitlerFascistPower?>,
    val hitlerChancellorWinRequirement: Int,
    val vetoUnlockRequirement: Int,
) {
    val fascistWinRequirement: Int = fascistPowers.size + 1

    fun fascistPowerAt(fascistPoliciesEnacted: Int): SecretHitlerFascistPower? {
        return fascistPowers[fascistPoliciesEnacted]
    }
}
