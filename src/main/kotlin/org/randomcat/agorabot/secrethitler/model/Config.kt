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
        return fascistPowers[fascistPoliciesEnacted - 1]
    }
}


data class SecretHitlerRoleConfiguration(
    val liberalCount: Int,
    val plainFascistCount: Int,
    val hitlerKnowsFascists: Boolean,
) {
    init {
        require(liberalCount > 0)
        require(plainFascistCount > 0)
    }

    // Liberals + Plain Fascists + 1 Hitler
    val totalRoles
        get() = liberalCount + plainFascistCount + 1
}

data class SecretHitlerStartConfiguration(
    val roleConfiguration: SecretHitlerRoleConfiguration,
    val gameConfiguration: SecretHitlerGameConfiguration,
)
