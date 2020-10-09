package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.Command
import org.randomcat.agorabot.CommandInvocation
import org.randomcat.agorabot.util.disallowMentions

interface BaseCommandArgumentStrategy {
    fun argumentParseError(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    )
}

interface BaseCommandOutputSink {
    fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String)
    fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message)

    fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    )
}

interface BaseCommandStrategy : BaseCommandArgumentStrategy, BaseCommandOutputSink

abstract class BaseCommand(private val strategy: BaseCommandStrategy) : Command {
    protected class ExecutionReceiverImpl(
        private val strategy: BaseCommandStrategy,
        private val event: MessageReceivedEvent,
        private val invocation: CommandInvocation,
    ) {
        fun respond(message: String) {
            strategy.sendResponse(event, invocation, message)
        }

        fun respond(message: Message) {
            strategy.sendResponseMessage(event, invocation, message)
        }

        fun respondWithFile(fileName: String, fileContent: String) {
            strategy.sendResponseAsFile(event, invocation, fileName, fileContent)
        }

        fun currentMessageEvent() = event
        fun currentChannel() = currentMessageEvent().channel
        fun currentGuildId(): String = currentMessageEvent().guild.id
    }

    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiverImpl>(
            UnparsedCommandArgs(invocation.args),
            onError = { msg ->
                strategy.argumentParseError(
                    event = event,
                    invocation = invocation,
                    errorMessage = msg,
                    usage = usage()
                )
            },
            ExecutionReceiverImpl(strategy, event, invocation),
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl()

    fun usage(): String {
        return UsageTopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>().apply { impl() }.usage()
    }
}

val DEFAULT_BASE_COMMAND_STRATEGY = object : BaseCommandStrategy {
    override fun argumentParseError(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        sendResponse(event, invocation, "$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseMessage(event: MessageReceivedEvent, invocation: CommandInvocation, message: Message) {
        event.channel.sendMessage(message).disallowMentions().queue()
    }

    override fun sendResponseAsFile(
        event: MessageReceivedEvent,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        val bytes = fileContent.toByteArray(Charsets.UTF_8)
        event.channel.sendFile(bytes, fileName).disallowMentions().queue()
    }
}
