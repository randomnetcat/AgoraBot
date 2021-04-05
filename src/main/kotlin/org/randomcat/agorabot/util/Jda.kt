package org.randomcat.agorabot.util

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
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

fun emptyRestActionOn(jda: JDA): RestAction<Unit> = CompletedRestAction.ofSuccess(jda, Unit)

inline fun <T> ignoringRestActionOn(jda: JDA, actionFun: () -> RestAction<T>?): RestAction<Unit> {
    return try {
        actionFun()?.also { check(it.jda === jda) }?.ignoreErrors() ?: emptyRestActionOn(jda)
    } catch (e: Exception) {
        emptyRestActionOn(jda)
    }
}

fun Message.tryAddReaction(reaction: String): RestAction<Unit> {
    return ignoringRestActionOn(jda) { addReaction(reaction) }
}

fun <T> RestAction<T>.ignoreErrors() = map { Unit }.onErrorMap { Unit }

fun MessageChannel.forwardHistorySequence() = sequence {
    val oldestMessageList = getHistoryFromBeginning(1).complete()

    check(oldestMessageList.size() <= 1)
    if (oldestMessageList.isEmpty) return@sequence

    var lastRetrievedMessage = oldestMessageList.retrievedHistory.single()
    yield(lastRetrievedMessage)

    while (true) {
        val nextHistory =
            getHistoryAfter(lastRetrievedMessage, JDA_HISTORY_MAX_RETRIEVE_LIMIT).complete()

        if (nextHistory.isEmpty) {
            return@sequence
        }

        // retrievedHistory is always newest -> oldest, we want oldest -> newest
        val nextMessages = nextHistory.retrievedHistory.asReversed()

        yieldAll(nextMessages)
        lastRetrievedMessage = nextMessages.last()
    }
}
