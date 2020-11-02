package org.randomcat.agorabot.util

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.restaction.MessageAction

fun MessageAction.disallowMentions() = allowedMentions(emptyList())

typealias DiscordMessage = net.dv8tion.jda.api.entities.Message
typealias DiscordPermission = net.dv8tion.jda.api.Permission

fun Guild.resolveRoleString(roleString: String): Role? {
    val cleanRoleString = roleString.removePrefix("@").toLowerCase()

    if (cleanRoleString == "everyone") return publicRole

    val byId = cleanRoleString.toLongOrNull()?.let { getRoleById(it) }
    if (byId != null) return byId

    val byName = getRolesByName(cleanRoleString, /*ignoreCase=*/ true)
    if (byName.size == 1) return byName.single()

    return null
}
