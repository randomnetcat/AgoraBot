package org.randomcat.agorabot.listener

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.randomcat.agorabot.irc.sendSplitMultiLineMessage
import org.randomcat.agorabot.util.disallowMentions
import org.randomcat.agorabot.util.ignoringRestActionOn


interface CommandRegistry {
    /**
     * Invokes the specified command
     */
    fun invokeCommand(source: CommandEventSource, invocation: CommandInvocation)
}

interface QueryableCommandRegistry : CommandRegistry {
    fun commands(): ImmutableMap<String, Command>
}

sealed class CommandEventSource {
    data class Discord(val event: MessageReceivedEvent) : CommandEventSource()
    data class Irc(val event: ChannelMessageEvent) : CommandEventSource()
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

interface Command {
    fun invoke(source: CommandEventSource, invocation: CommandInvocation)
}

typealias UnknownCommandHook = (CommandEventSource, CommandInvocation) -> Unit

private fun defaultUnknownCommand(source: CommandEventSource, commandInvocation: CommandInvocation) {
    source.tryRespondWithText("Unknown command \"${commandInvocation.command}\".")
}

data class MapCommandRegistry(
    private val registry: ImmutableMap<String, Command>,
    private val unknownCommandHook: UnknownCommandHook,
) : QueryableCommandRegistry {
    constructor(
        registry: Map<String, Command>,
        unknownCommandHook: UnknownCommandHook = ::defaultUnknownCommand,
    ) : this(
        registry.toImmutableMap(),
        unknownCommandHook,
    )

    override fun commands(): ImmutableMap<String, Command> {
        return registry
    }

    override fun invokeCommand(source: CommandEventSource, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(source, invocation) ?: unknownCommandHook(source, invocation)
    }
}

class MutableMapCommandRegistry(
    registry: Map<String, Command>,
    private val unknownCommandHook: UnknownCommandHook = ::defaultUnknownCommand,
) : QueryableCommandRegistry {
    private val registry: MutableMap<String, Command> = registry.toMutableMap() // Defensive copy

    fun addCommand(name: String, command: Command) {
        require(!registry.containsKey(name)) { "Cannot insert duplicate command $name" }
        registry[name] = command
    }

    fun addCommands(map: Map<String, Command>) {
        map.forEach { addCommand(it.key, it.value) }
    }

    override fun commands(): ImmutableMap<String, Command> {
        return registry.toImmutableMap()
    }

    override fun invokeCommand(source: CommandEventSource, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(source, invocation) ?: unknownCommandHook(source, invocation)
    }
}
