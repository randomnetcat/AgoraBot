package org.randomcat.agorabot.secrethitler.handlers

import net.dv8tion.jda.api.entities.MessageEmbed
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGlobalGameState

fun SecretHitlerGlobalGameState.liberalPoliciesEmbedField(inline: Boolean = true): MessageEmbed.Field {
    return MessageEmbed.Field(
        "Liberal Policies",
        "${boardState.policiesState.liberalPoliciesEnacted} / ${configuration.liberalWinRequirement}",
        inline,
    )
}

fun SecretHitlerGlobalGameState.fascistPoliciesEmbedField(inline: Boolean = true): MessageEmbed.Field {
    return MessageEmbed.Field(
        "Fascist Policies",
        "${boardState.policiesState.fascistPoliciesEnacted} / ${configuration.fascistWinRequirement}",
        inline,
    )
}

fun SecretHitlerGlobalGameState.electionTrackerEmbedField(inline: Boolean = true): MessageEmbed.Field {
    return MessageEmbed.Field(
        "Election Tracker",
        "${electionState.electionTrackerState} / ${configuration.speedyEnactRequirement}",
        inline,
    )
}
