package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

data class CommandInvocation(val command: String, val args: ImmutableList<String>) {
    constructor(command: String, args: List<String>) : this(command, args.toImmutableList())
}

sealed class CommandParseResult {
    data class Invocation(val invocation: CommandInvocation) : CommandParseResult()
    data class Message(val message: String) : CommandParseResult()
    object Ignore : CommandParseResult()
}

interface CommandParser {
    fun parse(event: MessageReceivedEvent): CommandParseResult
}
