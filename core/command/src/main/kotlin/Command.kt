package org.randomcat.agorabot.listener

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.randomcat.agorabot.irc.sendSplitMultiLineMessage
import org.randomcat.agorabot.util.disallowMentions
import org.randomcat.agorabot.util.ignoringRestActionOn

sealed class CommandEventSource {
    data class Discord(val event: MessageReceivedEvent) : CommandEventSource()
    data class Irc(val event: ChannelMessageEvent) : CommandEventSource()
}

data class CommandInvocation(val command: String, val args: ImmutableList<String>) {
    constructor(command: String, args: List<String>) : this(command, args.toImmutableList())
}

fun CommandEventSource.tryRespondWithText(message: String) {
    return when (this) {
        is CommandEventSource.Discord -> {
            ignoringRestActionOn(event.jda) {
                event.channel.sendMessage(message).disallowMentions()
            }.queue()
        }

        is CommandEventSource.Irc -> {
            event.channel.sendSplitMultiLineMessage(message)
        }
    }
}

val CommandEventSource.messageText: String
    get() = when (this) {
        is CommandEventSource.Discord -> event.message.contentRaw
        is CommandEventSource.Irc -> event.message
    }

interface Command {
    fun invoke(source: CommandEventSource, invocation: CommandInvocation)
}
