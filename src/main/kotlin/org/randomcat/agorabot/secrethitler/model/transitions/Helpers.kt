package org.randomcat.agorabot.secrethitler.model.transitions

import org.randomcat.agorabot.secrethitler.model.SecretHitlerDeckState
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState

fun SecretHitlerGlobalGameState.withDeckState(
    newDeckState: SecretHitlerDeckState,
): SecretHitlerGlobalGameState {
    return this.copy(
        boardState = this.boardState.copy(
            deckState = newDeckState,
        )
    )
}
