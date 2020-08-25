package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.events.message.MessageReceivedEvent


interface CommandInvoker {
    /**
     * Invokes the specified command
     */
    fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation)
}

class NullCommandInvoker : CommandInvoker {
    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        /* do nothing */
    }
}

interface Command {
    fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation)
}

class MapCommandInvoker : CommandInvoker {
    companion object {
        private fun defaultUnknownCommand(event: MessageReceivedEvent, commandInvocation: CommandInvocation) {
            event.channel.sendMessage("Unknown command \"${commandInvocation.command}\".").queue()
        }
    }

    private val registry: ImmutableMap<String, Command>
    private val unknownCommandHook: (MessageReceivedEvent, CommandInvocation) -> Unit

    constructor(
        registry: Map<String, Command>,
        unknownCommandHook: (MessageReceivedEvent, CommandInvocation) -> Unit = ::defaultUnknownCommand
    ) {
        this.registry = registry.toImmutableMap()
        this.unknownCommandHook = unknownCommandHook
    }

    override fun invokeCommand(event: MessageReceivedEvent, invocation: CommandInvocation) {
        registry[invocation.command]?.invoke(event, invocation) ?: unknownCommandHook(event, invocation)
    }
}
