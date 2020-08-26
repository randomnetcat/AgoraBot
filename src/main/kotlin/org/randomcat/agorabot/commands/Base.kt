package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.Command
import org.randomcat.agorabot.CommandInvocation

interface BaseCommandStrategy {
    fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String)
    fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String)
}

interface ReadableCommandArgumentParseError {
    val message: String
}

fun ReadableCommandArgumentParseError(msg: String) = object : ReadableCommandArgumentParseError {
    override val message: String
        get() = msg
}

private class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver
) : ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private var alreadyCalled = false
    private var alreadyUsed = false

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        if (alreadyCalled) return

        val parseResult = parseCommandArgs(parsers.asList(), arguments)

        if (parseResult is CommandArgumentParseSuccess && parseResult.remaining.args.isEmpty()) {
            alreadyCalled = true
            exec(receiver, parseResult.value)
        }
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(!alreadyUsed)
        block()
        alreadyUsed = true
        if (!alreadyCalled) onNoMatch()
    }
}

private class TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onError: (message: String) -> Unit,
    private val receiver: ExecutionReceiver
) : TopLevelArgumentDescriptionReceiver<ExecutionReceiver> {
    private var alreadyParsed: Boolean = false

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        check(!alreadyParsed)

        val result = parseCommandArgs(parsers.asList(), arguments)

        return when (result) {
            is CommandArgumentParseResult.Success -> {
                val remaining = result.remaining.args

                // Only execute if there are no remaining arguments - users can opt-in to accepting remaining arguments
                // with special argument.
                if (remaining.isEmpty()) {
                    exec(receiver, result.value)
                } else {
                    reportError(
                        index = result.value.size + 1,
                        ReadableCommandArgumentParseError("extraneous arg: ${remaining.first()}")
                    )
                }
            }

            is CommandArgumentParseResult.Failure -> {
                reportError(index = result.error.index, error = result.error.error)
            }
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(!alreadyParsed)

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onNoMatch = {
                alreadyParsed = true
                onError("No match for command set")
            },
            receiver = receiver
        ).executeWholeBlock(block)

        alreadyParsed = true
    }

    private fun <E> reportError(index: Int, error: E) {
        val prefix = "Error while parsing argument $index"
        val message = if (error is ReadableCommandArgumentParseError) "$prefix: ${error.message}" else prefix

        onError(message)
    }
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
    }

    override fun invoke(event: MessageReceivedEvent, invocation: CommandInvocation) {
        TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiverImpl>(
            UnparsedCommandArgs(invocation.args),
            onError = { msg -> strategy.argumentParseError(event, invocation, msg) },
            ExecutionReceiverImpl(strategy, event, invocation),
        ).impl()
    }

    protected abstract fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl()

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

abstract class ChatCommand : BaseCommand(object : BaseCommandStrategy {
    override fun argumentParseError(event: MessageReceivedEvent, invocation: CommandInvocation, errorMessage: String) {
        sendResponse(event, invocation, errorMessage)
    }

    override fun sendResponse(event: MessageReceivedEvent, invocation: CommandInvocation, message: String) {
        event.channel.sendMessage(message).queue()
    }
})
