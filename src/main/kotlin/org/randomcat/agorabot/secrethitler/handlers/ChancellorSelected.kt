package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.secrethitler.buttons.SecretHitlerChancellorCandidateSelectionButtonDescriptor
import org.randomcat.agorabot.util.handleTextResponse

internal fun doHandleSecretHitlerChancellorSelect(
    repository: SecretHitlerRepository,
    nameContext: SecretHitlerNameContext,
    event: ButtonClickEvent,
    request: SecretHitlerChancellorCandidateSelectionButtonDescriptor,
) {
    handleTextResponse(event) {
        "You chose player number ${request.selectedChancellor.raw}"
    }
}
