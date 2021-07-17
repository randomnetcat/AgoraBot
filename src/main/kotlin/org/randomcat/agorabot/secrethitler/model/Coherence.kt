package org.randomcat.agorabot.secrethitler.model

private fun SecretHitlerEphemeralState.countPoliciesOf(type: SecretHitlerPolicyType): Int {
    return when (this) {
        is SecretHitlerEphemeralState.ChancellorPolicyChoicePending -> {
            options.policies.count { it == type }
        }

        is SecretHitlerEphemeralState.PresidentPolicyChoicePending -> {
            options.policies.count { it == type }
        }

        is SecretHitlerEphemeralState.ChancellorSelectionPending -> 0
        is SecretHitlerEphemeralState.PolicyPending -> 0
        is SecretHitlerEphemeralState.VotingOngoing -> 0
    }
}

private fun SecretHitlerPoliciesState.countPoliciesOf(type: SecretHitlerPolicyType): Int {
    return when (type) {
        SecretHitlerPolicyType.LIBERAL -> liberalPoliciesEnacted
        SecretHitlerPolicyType.FASCIST -> fascistPoliciesEnacted
    }
}

private fun SecretHitlerDeckState.countPoliciesOf(type: SecretHitlerPolicyType): Int {
    return drawDeck.allPolicies().count { it == type } + discardDeck.allPolicies().count { it == type }
}

private fun SecretHitlerGlobalGameState.countPoliciesOf(type: SecretHitlerPolicyType): Int {
    return boardState.policiesState.countPoliciesOf(type) + boardState.deckState.countPoliciesOf(type)
}

private fun requireValidLiberalCount(
    globalGameState: SecretHitlerGlobalGameState,
    ephemeralState: SecretHitlerEphemeralState,
) {
    val actualLiberalCount =
        globalGameState.countPoliciesOf(SecretHitlerPolicyType.LIBERAL) +
                ephemeralState.countPoliciesOf(SecretHitlerPolicyType.LIBERAL)

    require(actualLiberalCount == SecretHitlerDeckState.TOTAL_LIBERAL_COUNT) {
        "Expected ${SecretHitlerDeckState.TOTAL_LIBERAL_COUNT} liberal policies, but got $actualLiberalCount"
    }
}

private fun requireValidFascistCount(
    globalGameState: SecretHitlerGlobalGameState,
    ephemeralState: SecretHitlerEphemeralState,
) {
    val actualFascistCount =
        globalGameState.countPoliciesOf(SecretHitlerPolicyType.FASCIST) +
                ephemeralState.countPoliciesOf(SecretHitlerPolicyType.FASCIST)

    require(actualFascistCount == SecretHitlerDeckState.TOTAL_FASCIST_COUNT) {
        "Expected ${SecretHitlerDeckState.TOTAL_FASCIST_COUNT} fascist policies, but got $actualFascistCount"
    }
}

private fun requireSufficientDeckUnlessPolicySelectionOngoing(
    globalState: SecretHitlerGlobalGameState,
    ephemeralState: SecretHitlerEphemeralState,
) {
    val deskMustBeDrawable = when (ephemeralState) {
        is SecretHitlerEphemeralState.PresidentPolicyChoicePending -> false
        is SecretHitlerEphemeralState.ChancellorPolicyChoicePending -> false
        else -> true
    }

    if (deskMustBeDrawable) {
        val actualPolicyCount = globalState.boardState.deckState.drawDeck.policyCount

        require(actualPolicyCount >= SecretHitlerDrawDeckState.STANDARD_DRAW_AMOUNT) {
            "Expected at least ${SecretHitlerDrawDeckState.STANDARD_DRAW_AMOUNT} cards in draw deck, " +
                    "but got $actualPolicyCount"
        }
    }
}

fun requireCoherent(globalGameState: SecretHitlerGlobalGameState, ephemeralState: SecretHitlerEphemeralState) {
    requireValidLiberalCount(globalGameState, ephemeralState)
    requireValidFascistCount(globalGameState, ephemeralState)
    requireSufficientDeckUnlessPolicySelectionOngoing(globalGameState, ephemeralState)
}
