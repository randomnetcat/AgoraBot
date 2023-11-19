package org.randomcat.agorabot.listener

import org.randomcat.agorabot.util.splitArguments

sealed class CommandParseResult {
    data class Invocation(val invocation: CommandInvocation) : CommandParseResult()
    object Ignore : CommandParseResult()
}

interface CommandParser {
    fun parse(source: CommandEventSource): CommandParseResult
}

/**
 * Parses a command as if by splitArguments, after removing [prefix]. The first argument is the command name, the rest
 * are the actual arguments. If the prefix is not present, or if there is no command after the prefix, returns Ignore.
 *
 * Throws [IllegalArgumentException] if [prefix] is empty.
 */
fun parsePrefixCommand(prefix: String, message: String): CommandParseResult {
    val effectiveMessage = message.removePrefix("\\")

    require(prefix.isNotEmpty())

    val payload = effectiveMessage.removePrefix(prefix)

    // If the prefix was not there to remove (when payload == message), there is no prefix, so no command.
    if (payload == effectiveMessage) return CommandParseResult.Ignore

    return parseNoPrefixCommand(payload)
}

private fun parseNoPrefixCommand(message: String): CommandParseResult {
    val parts = splitArguments(message)
    if (parts.isEmpty()) return CommandParseResult.Ignore // Just a prefix, for some reason

    return CommandParseResult.Invocation(CommandInvocation(parts.first(), parts.drop(1)))
}

fun parsePrefixListCommand(prefixOptions: Iterable<String>, message: String): CommandParseResult {
    for (prefixOption in prefixOptions) {
        require(prefixOption.isNotEmpty())

        val parsed = parsePrefixCommand(prefixOption, message)
        if (parsed !is CommandParseResult.Ignore) return parsed
    }

    return CommandParseResult.Ignore
}

interface GuildPrefixMap {
    fun prefixesForGuild(guildId: String): Iterable<String>
}

interface MutableGuildPrefixMap : GuildPrefixMap {
    fun addPrefixForGuild(guildId: String, prefix: String)
    fun removePrefixForGuild(guildId: String, prefix: String)
    fun clearPrefixesForGuild(guildId: String)
}

class GlobalPrefixCommandParser(private val prefix: String) : CommandParser {
    override fun parse(source: CommandEventSource): CommandParseResult = parsePrefixCommand(
        prefix = prefix,
        message = source.messageText
    )
}

class GuildPrefixCommandParser(private val map: GuildPrefixMap) : CommandParser {
    override fun parse(source: CommandEventSource): CommandParseResult {
        if (source !is CommandEventSource.Discord) return CommandParseResult.Ignore

        val event = source.event

        return if (event.isFromGuild)
            parsePrefixListCommand(
                prefixOptions = map.prefixesForGuild(event.guild.id),
                message = event.message.contentRaw,
            )
        else
            parseNoPrefixCommand(message = event.message.contentRaw)
    }
}

class MentionPrefixCommandParser(private val fallback: CommandParser) : CommandParser {
    override fun parse(source: CommandEventSource): CommandParseResult {
        if (source is CommandEventSource.Discord) {
            val event = source.event

            val selfUserId = event.jda.selfUser.id
            val selfRoleId =
                event.takeIf { it.isFromGuild }?.guild?.selfMember?.roles?.singleOrNull { it.isManaged }?.id

            // These are the two options for raw mentions; see https://discord.com/developers/docs/reference
            val mentionOptions = listOfNotNull("<@$selfUserId>", "<@!$selfUserId>", selfRoleId?.let { "<@&$it>" })

            val parseResult = parsePrefixListCommand(prefixOptions = mentionOptions, message = event.message.contentRaw)
            if (parseResult !is CommandParseResult.Ignore) return parseResult
        }

        return fallback.parse(source)
    }
}
