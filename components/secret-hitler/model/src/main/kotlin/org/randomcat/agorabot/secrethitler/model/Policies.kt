package org.randomcat.agorabot.secrethitler.model

import org.randomcat.agorabot.secrethitler.model.transitions.SecretHitlerWinResult

enum class SecretHitlerPolicyType {
    FASCIST,
    LIBERAL,
}

val SecretHitlerPolicyType.readableName
    get() = when (this) {
        SecretHitlerPolicyType.LIBERAL -> "Liberal"
        SecretHitlerPolicyType.FASCIST -> "Fascist"
    }


enum class SecretHitlerFascistPower {
    EXAMINE_CARDS,
    INVESTIGATE_PARTY,
    SPECIAL_ELECTION,
    EXECUTE_PLAYER,
}

val SecretHitlerFascistPower.readableName: String
    get() = when (this) {
        SecretHitlerFascistPower.EXAMINE_CARDS -> "Policy Peek"
        SecretHitlerFascistPower.SPECIAL_ELECTION -> "Special Election"
        SecretHitlerFascistPower.INVESTIGATE_PARTY -> "Investigate Loyalty"
        SecretHitlerFascistPower.EXECUTE_PLAYER -> "Execute Player"
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

    sealed class EnactmentResult {
        data class GameEnds(val winResult: SecretHitlerWinResult) : EnactmentResult()

        data class GameContinues(
            val newPolicyState: SecretHitlerPoliciesState,
            val fascistPower: SecretHitlerFascistPower?,
        ) : EnactmentResult()
    }

    fun withFascistPolicyEnacted(config: SecretHitlerGameConfiguration): EnactmentResult {
        val newFascistPolicyCount = fascistPoliciesEnacted + 1

        if (newFascistPolicyCount >= config.fascistWinRequirement) {
            return EnactmentResult.GameEnds(SecretHitlerWinResult.FascistsWin.FascistPolicyGoalReached)
        }

        return EnactmentResult.GameContinues(
            newPolicyState = this.copy(fascistPoliciesEnacted = newFascistPolicyCount),
            fascistPower = config.fascistPowerAt(fascistPoliciesEnacted = newFascistPolicyCount),
        )
    }

    fun withLiberalPolicyEnacted(config: SecretHitlerGameConfiguration): EnactmentResult {
        val newLiberalPolicyCount = liberalPoliciesEnacted + 1

        if (newLiberalPolicyCount >= config.liberalWinRequirement) {
            return EnactmentResult.GameEnds(SecretHitlerWinResult.LiberalsWin.LiberalPolicyGoalReached)
        }

        return EnactmentResult.GameContinues(
            newPolicyState = this.copy(liberalPoliciesEnacted = newLiberalPolicyCount),
            fascistPower = null,
        )
    }

    fun withPolicyEnacted(config: SecretHitlerGameConfiguration, type: SecretHitlerPolicyType): EnactmentResult {
        return when (type) {
            SecretHitlerPolicyType.LIBERAL -> withLiberalPolicyEnacted(config)
            SecretHitlerPolicyType.FASCIST -> withFascistPolicyEnacted(config)
        }
    }
}
