package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


interface CommandRegistry {
    /**
     * Invokes the specified command
     */
    fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation)
}

interface QueryableCommandRegistry : CommandRegistry {
    fun commands(): ImmutableMap<String, Command>
}

class NullCommandRegistry : CommandRegistry {
    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        /* do nothing */
    }
}

interface Command {
    fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation)
}

typealias UnknownCommandHook = (MessageReceivedEvent, CommandInvocation) -> Unit

private fun defaultUnknownCommand(event: MessageReceivedEvent, commandInvocation: CommandInvocation) {
    event.channel.sendMessage("Unknown command \"${commandInvocation.command}\".").disallowMentions().queue()
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

    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(event, invocation) ?: unknownCommandHook(event, invocation)
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

    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(event, invocation) ?: unknownCommandHook(event, invocation)
    }
}
