package org.randomcat.agorabot.util

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.MessageAction

const val JDA_HISTORY_MAX_RETRIEVE_LIMIT = 100
const val DISCORD_MAX_MESSAGE_LENGTH = 2000

fun MessageAction.disallowMentions() = allowedMentions(emptyList())

typealias DiscordMessage = net.dv8tion.jda.api.entities.Message
typealias DiscordPermission = net.dv8tion.jda.api.Permission

fun String.asSnowflakeOrNull(): Long? {
    return toLongOrNull()
}

fun Guild.resolveRoleString(roleString: String): Role? {
    val cleanRoleString = roleString.removePrefix("@").toLowerCase()

    if (cleanRoleString == "everyone") return publicRole

    val byId = cleanRoleString.asSnowflakeOrNull()?.let { getRoleById(it) }
    if (byId != null) return byId

    val byName = getRolesByName(cleanRoleString, /*ignoreCase=*/ true)
    if (byName.size == 1) return byName.single()

    return null
}

fun Message.tryAddReaction(reaction: String): RestAction<Unit> {
    return try {
        addReaction(reaction).ignoreErrors()
    } catch (e: Exception) {
        CompletedRestAction.ofSuccess(jda, Unit)
    }
}

fun <T> RestAction<T>.ignoreErrors() = map { Unit }.onErrorMap { Unit }
