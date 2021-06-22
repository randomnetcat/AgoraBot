package org.randomcat.agorabot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.future.await
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
    val cleanRoleString = roleString.removePrefix("@").lowercase()

    if (cleanRoleString == "everyone") return publicRole

    val byId = cleanRoleString.asSnowflakeOrNull()?.let { getRoleById(it) }
    if (byId != null) return byId

    val byName = getRolesByName(cleanRoleString, /*ignoreCase=*/ true)
    if (byName.size == 1) return byName.single()

    return null
}

fun emptyRestActionOn(jda: JDA): RestAction<Unit> = CompletedRestAction.ofSuccess(jda, Unit)

inline fun <T> ignoringRestActionOn(jda: JDA, actionFun: () -> RestAction<T>?): RestAction<Unit> {
    val action = try {
        actionFun()
    } catch (e: Exception) {
        null
    }

    return action?.also { check(it.jda === jda) }?.ignoreErrors() ?: emptyRestActionOn(jda)
}

val Message.effectiveSenderName: String
    get() = member?.effectiveName ?: author.name

fun Message.tryAddReaction(reaction: String): RestAction<Unit> {
    return ignoringRestActionOn(jda) { addReaction(reaction) }
}

fun <T> RestAction<T>.ignoreErrors() = map { Unit }.onErrorMap { Unit }

suspend fun <T> RestAction<T>.await() = submit().await()

private fun MessageChannel.retrieveEarliestMessage(): RestAction<Message?> {
    return getHistoryFromBeginning(1).map {
        when (it.size()) {
            0 -> null
            1 -> it.retrievedHistory.single()
            else -> error("Expected at most 1 element")
        }
    }
}

fun MessageChannel.forwardHistorySequence() = sequence {
    var lastRetrievedMessage = retrieveEarliestMessage().complete() ?: return@sequence
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

fun CoroutineScope.forwardHistoryChannelOf(
    discordChannel: MessageChannel,
    bufferCapacity: Int = 0,
): ReceiveChannel<Message> {
    require(bufferCapacity != Channel.CONFLATED) // CONFLATED makes no sense here

    @OptIn(ExperimentalCoroutinesApi::class)
    return produce(Dispatchers.IO, capacity = bufferCapacity) {
        var lastRetrievedMessage = discordChannel.retrieveEarliestMessage().await() ?: return@produce
        send(lastRetrievedMessage)

        while (true) {
            val nextHistory =
                discordChannel.getHistoryAfter(lastRetrievedMessage, JDA_HISTORY_MAX_RETRIEVE_LIMIT).await()

            if (nextHistory.isEmpty) {
                return@produce
            }

            // retrievedHistory is always newest -> oldest, we want oldest -> newest
            val nextMessages = nextHistory.retrievedHistory.asReversed()

            nextMessages.forEach { send(it) }
            lastRetrievedMessage = nextMessages.last()
        }
    }
}
