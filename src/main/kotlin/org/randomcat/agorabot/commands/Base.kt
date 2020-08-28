package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.Command
import org.randomcat.agorabot.CommandInvocation

interface BaseCommandStrategy {
    fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String)
    fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String)
}

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    protected class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val event: MessageReceivedEvent,
        private val invocation: CommandInvocation,
    ) {
        fun respond(message: String) {
            strategy.sendResponse(event, invocation, message)
        }

        fun currentGuildId(): String = event.guild.id
    }

    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiverImpl>(
            UnparsedCommandArgs(invocation.args),
            onError = { msg -> strategy.argumentParseError(event, invocation, msg) },
            ExecutionReceiverImpl(strategy, event, invocation),
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl()
}

abstract class ChatCommand : BaseCommand(object : BaseCommandStrategy {
    override fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String) {
        sendResponse(event, invocation, errorMessage)
    }

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        event.channel.sendMessage(message).queue()
    }
})
