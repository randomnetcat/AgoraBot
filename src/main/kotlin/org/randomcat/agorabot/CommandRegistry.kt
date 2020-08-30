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

class NullCommandRegistry : CommandRegistry {
    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        /* do nothing */
    }
}

interface Command {
    fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation)
}

typealias UnknownCommandHook = (MessageReceivedEvent, CommandInvocation) -> Unit

data class MapCommandRegistry(
    private val registry: ImmutableMap<String, Command>,
    private val unknownCommandHook: UnknownCommandHook,
) : CommandRegistry {
    companion object {
        private fun defaultUnknownCommand(event: MessageReceivedEvent, commandInvocation: CommandInvocation) {
            event.channel.sendMessage("Unknown command \"${commandInvocation.command}\".").queue()
        }
    }

    constructor(
        registry: Map<String, Command>,
        unknownCommandHook: UnknownCommandHook = ::defaultUnknownCommand,
    ) : this(
        registry.toImmutableMap(),
        unknownCommandHook,
    )

    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(event, invocation) ?: unknownCommandHook(event, invocation)
    }
}
