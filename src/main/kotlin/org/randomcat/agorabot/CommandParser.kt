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

/**
 * Parses a command as if by splitArguments, after removing [prefix]. The first argument is the command name, the rest
 * are the actual arguments. If the prefix is not present, or if there is no command after the prefix, returns Ignore.
 *
 * Throws [IllegalArgumentException] if [prefix] is empty.
 */
fun parsePrefixCommand(prefix: String, message: String): CommandParseResult {
    require(prefix.isNotEmpty())

    val payload = message.removePrefix(prefix)

    // If the prefix was not there to remove (when payload == message), there is no prefix, so no command.
    if (payload == message) return CommandParseResult.Ignore

    val parts = splitArguments(payload)
    if (parts.isEmpty()) return CommandParseResult.Ignore // Just a prefix, for some reason

    return CommandParseResult.Invocation(CommandInvocation(parts.first(), parts.drop(1)))
}

interface GuildPrefixMap {
    fun prefixForGuild(guildId: String): String
}

interface MutableGuildPrefixMap : GuildPrefixMap {
    fun setPrefixForGuild(guildId: String, prefix: String)
}

class GlobalPrefixCommandParser(private val prefix: String) : CommandParser {
    override fun parse(event: MessageReceivedEvent): CommandParseResult = parsePrefixCommand(
        prefix = prefix,
        message = event.message.contentRaw
    )
}

class GuildPrefixCommandParser(private val map: GuildPrefixMap) : CommandParser {
    override fun parse(event: MessageReceivedEvent): CommandParseResult = parsePrefixCommand(
        prefix = map.prefixForGuild(event.guild.id),
        message = event.message.contentRaw,
    )
}
