package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerFascistPower
import org.randomcat.agorabot.secrethitler.model.SecretHitlerPlayerNumber

sealed class SecretHitlerFascistPowerStateResult {
    data class Stateful(
        val ephemeralState: SecretHitlerEphemeralState.PolicyPending,
    ) : SecretHitlerFascistPowerStateResult()

    sealed class Stateless : SecretHitlerFascistPowerStateResult()
    object PolicyPeek : Stateless()
}

private fun SecretHitlerEphemeralState.PolicyPending.toResult() =
    SecretHitlerFascistPowerStateResult.Stateful(this)

fun secretHitlerFascistPowerEphemeralState(
    fascistPower: SecretHitlerFascistPower,
    presidentNumber: SecretHitlerPlayerNumber,
): SecretHitlerFascistPowerStateResult = when (fascistPower) {
    SecretHitlerFascistPower.INVESTIGATE_PARTY -> {
        SecretHitlerEphemeralState.PolicyPending.InvestigateParty(
            presidentNumber = presidentNumber,
        ).toResult()
    }

    SecretHitlerFascistPower.SPECIAL_ELECTION -> {
        SecretHitlerEphemeralState.PolicyPending.SpecialElection(
            presidentNumber = presidentNumber,
        ).toResult()
    }

    SecretHitlerFascistPower.EXAMINE_CARDS -> {
        SecretHitlerFascistPowerStateResult.PolicyPeek
    }

    SecretHitlerFascistPower.EXECUTE_PLAYER -> {
        SecretHitlerEphemeralState.PolicyPending.Execution(
            presidentNumber = presidentNumber,
        ).toResult()
    }
}
