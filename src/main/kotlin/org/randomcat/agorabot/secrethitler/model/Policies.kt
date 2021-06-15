package org.randomcat.agorabot.secrethitler.model

enum class SecretHitlerPolicyType {
    FASCIST,
    LIBERAL,
}

enum class SecretHitlerFascistPower {
    EXAMINE_CARDS,
    INVESTIGATE_PARTY,
    SPECIAL_ELECTION,
    EXECUTE_PLAYER,
}

sealed class SecretHitlerPoliciesEnactmentResult {
    sealed class ImmediateWin : SecretHitlerPoliciesEnactmentResult() {
        object LiberalWin : ImmediateWin()
        object FascistWin : ImmediateWin()
    }

    data class GameContinues(
        val newPolicyState: SecretHitlerPoliciesState,
        val fascistPower: SecretHitlerFascistPower?,
    ) : SecretHitlerPoliciesEnactmentResult()
}

data class SecretHitlerPoliciesState(
    val liberalPoliciesEnacted: Int,
    val fascistPoliciesEnacted: Int,
) {
    constructor() : this(0, 0)

    init {
        require(liberalPoliciesEnacted >= 0)
        require(fascistPoliciesEnacted >= 0)

        // Prevent overflow
        require(liberalPoliciesEnacted < Int.MAX_VALUE)
        require(fascistPoliciesEnacted < Int.MAX_VALUE)
    }

    fun enactFascistPolicy(config: SecretHitlerGameConfiguration): SecretHitlerPoliciesEnactmentResult {
        val newFascistPolicyCount = fascistPoliciesEnacted + 1

        if (newFascistPolicyCount >= config.fascistWinRequirement) {
            return SecretHitlerPoliciesEnactmentResult.ImmediateWin.FascistWin
        }

        return SecretHitlerPoliciesEnactmentResult.GameContinues(
            newPolicyState = this.copy(fascistPoliciesEnacted = newFascistPolicyCount),
            fascistPower = config.fascistPowerAt(newFascistPolicyCount),
        )
    }

    fun enactLiberalPolicy(config: SecretHitlerGameConfiguration): SecretHitlerPoliciesEnactmentResult {
        val newLiberalPolicyCount = liberalPoliciesEnacted + 1

        if (newLiberalPolicyCount >= config.liberalWinRequirement) {
            return SecretHitlerPoliciesEnactmentResult.ImmediateWin.LiberalWin
        }

        return SecretHitlerPoliciesEnactmentResult.GameContinues(
            newPolicyState = this.copy(liberalPoliciesEnacted = newLiberalPolicyCount),
            fascistPower = null,
        )
    }
}
