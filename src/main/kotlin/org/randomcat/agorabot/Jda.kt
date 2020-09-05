package org.randomcat.agorabot

import net.dv8tion.jda.api.requests.restaction.MessageAction

fun MessageAction.disallowMentions() = allowedMentions(emptyList())
