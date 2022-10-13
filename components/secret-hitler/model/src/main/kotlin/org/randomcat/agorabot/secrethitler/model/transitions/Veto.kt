package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerEphemeralState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameState

fun SecretHitlerGameState.Running.With<SecretHitlerEphemeralState.ChancellorPolicyChoicePending>.afterVetoApproved(
    shuffleProvider: SecretHitlerDeckState.ShuffleProvider,
): SecretHitlerInactiveGovernmentResult {
    require(ephemeralState.vetoState == SecretHitlerEphemeralState.VetoRequestState.REQUESTED)

    val globalStateAfterDiscard = globalState.withDeckState(
        newDeckState = SecretHitlerDeckState(
            drawDeck = this.globalState.boardState.deckState.drawDeck,
            discardDeck = this.globalState.boardState.deckState.discardDeck.afterDiscardingAll(ephemeralState.options.policies),
        ).shuffledIfDrawPileSmall(
            shuffleProvider = shuffleProvider,
        )
    )

    return globalStateAfterDiscard.afterInactiveGovernment(shuffleProvider)
}
