package org.randomcat.agorabot.secrethitler.model

interface SecretHitlerGameConfiguration {
    val liberalWinRequirement: Int
    val fascistWinRequirement: Int

    val hitlerChancellorWinRequirement: Int
    val vetoUnlockRequirement: Int

    fun fascistPowerAt(fascistPoliciesEnacted: Int): SecretHitlerFascistPower?
}
