package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.Command
import org.randomcat.agorabot.CommandInvocation

interface CommandErrorStrategy {
    fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String)
}

interface ReadableCommandArgumentParseError {
    val message: String
}

fun ReadableCommandArgumentParseError(msg: String) = object : ReadableCommandArgumentParseError {
    override val message: String
        get() = msg
}

private class CommandExecutionReceiverImpl<Args>(override val args: Args) : CommandExecutionReceiver<Args>

private class ExecutingArgumentDescriptionReceiver(
    private val arguments: UnparsedCommandArgs,
    private val onError: (message: String) -> Unit
) : ArgumentDescriptionReceiver {
    private var alreadyParsed: Boolean = false

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: CommandExecutionReceiverBlock<List<T>>
    ) {
        check(!alreadyParsed)

        val result = parseCommandArgs(parsers.asList(), arguments)

        return when (result) {
            is CommandArgumentParseResult.Success -> {
                exec(CommandExecutionReceiverImpl(result.value))
            }

            is CommandArgumentParseResult.Failure -> {
                reportError(index = result.error.index, error = result.error.error)
            }
        }
    }

    private fun <E> reportError(index: Int, error: E) {
        val prefix = "Error while parsing argument $index"
        val message = if (error is ReadableCommandArgumentParseError) "$prefix: ${error.message}" else prefix

        onError(message)
    }
}

abstract class BaseCommand(private val errorStrategy: CommandErrorStrategy) : Command {
    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        ExecutingArgumentDescriptionReceiver(
            UnparsedCommandArgs(invocation.args),
            onError = { msg -> errorStrategy.argumentParseError(event, invocation, msg) }
        ).impl()
    }

    abstract fun ArgumentDescriptionReceiver.impl()

    protected abstract class CommandArgument<T>(
        val name: String
    ) : CommandArgumentParser<T, ReadableCommandArgumentParseError> {
        abstract val type: String

        protected val noArgumentError =
            CommandArgumentParseResult.Failure(ReadableCommandArgumentParseError("no argument provided"))
    }

    protected fun IntArg(name: String) = object : CommandArgument<Int>(name) {
        override val type: String
            get() = "int"

        override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<Int, ReadableCommandArgumentParseError> {
            val args = arguments.args
            if (args.isEmpty()) return noArgumentError

            val firstArg = args.first()
            val intArg = firstArg.toIntOrNull()

            return if (intArg != null)
                CommandArgumentParseSuccess(intArg, arguments.tail())
            else
                CommandArgumentParseFailure(ReadableCommandArgumentParseError("invalid int: $firstArg"))
        }
    }

    protected fun StringArg(name: String) = object : CommandArgument<String>(name) {
        override val type: String
            get() = "string"

        override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<String, ReadableCommandArgumentParseError> {
            val args = arguments.args
            if (args.isEmpty()) return noArgumentError

            return CommandArgumentParseSuccess(args.first(), arguments.tail())
        }
    }
}

abstract class ChatCommand : BaseCommand(object : CommandErrorStrategy {
    override fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String) {
        event.channel.sendMessage(errorMessage).queue()
    }
})
