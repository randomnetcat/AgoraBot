package org.randomcat.agorabot.commands

abstract class BaseExecutingArgumentDescriptionReceiver {
    private var alreadyParsed: Boolean = false

    protected fun beginParsing() {
        check(!alreadyParsed)
        alreadyParsed = true
    }
}

abstract class BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>
    : BaseExecutingArgumentDescriptionReceiver(), ArgumentDescriptionReceiver<ExecutionReceiver> {
    protected abstract val onMatch: () -> Unit
    protected abstract val arguments: UnparsedCommandArgs
    protected abstract val receiver: ExecutionReceiver

    private var _alreadyCalled = false
    protected val alreadyCalled get() = _alreadyCalled

    protected fun markCalled() {
        _alreadyCalled = true
        onMatch()
    }

    protected fun <T, E> doArgsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments))

        if (parseResult != null) {
            markCalled()
            exec(receiver, parseResult.value)
        }
    }

    protected fun doMatchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = { markCalled() },
            endNoMatch = {},
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    companion object {
        /**
         * Returns [parseResult] if [parseResult] should be considered a successful parse, otherwise returns null.
         */
        @JvmStatic
        protected fun <T, E> filterParseResult(
            parseResult: CommandArgumentParseResult<T, E>
        ): CommandArgumentParseSuccess<T>? {
            return if (parseResult.isFullMatch()) parseResult as CommandArgumentParseSuccess else null
        }
    }
}

class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    override val arguments: UnparsedCommandArgs,
    override val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    override val receiver: ExecutionReceiver
) : BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>(),
    ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        if (alreadyCalled) return
        doArgsRaw(parsers.asList(), exec)
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        if (alreadyCalled) return
        doMatchFirst(block)
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        beginParsing()
        block()
        if (!alreadyCalled) endNoMatch()
    }
}

class SubcommandsExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    override val arguments: UnparsedCommandArgs,
    override val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    override val receiver: ExecutionReceiver
) : BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>(),
    SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> {
    private enum class State {
        Undetermined, Implementation, SubcommandParent
    }

    private var state: State = State.Undetermined
    private val seenSubcommands = mutableSetOf<String>()

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        check(state == State.Undetermined) { "cannot provide two subcommand implementations" }
        doArgsRaw(parsers.asList(), exec)
        state = State.Implementation
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(state == State.Undetermined) { "cannot provide two subcommand implementations" }
        doMatchFirst(block)
        state = State.Implementation
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(state == State.Undetermined || state == State.SubcommandParent) {
            "cannot provide both implementation and nested subcommands"
        }
        state = State.SubcommandParent

        check(!seenSubcommands.contains(name)) { "repeated definition of subcommand $name" }
        seenSubcommands.add(name)

        val argsList = arguments.args
        if (argsList.isEmpty()) return

        val firstArg = argsList.first()

        if (firstArg.equals(name, ignoreCase = true)) {
            SubcommandsExecutingArgumentDescriptionReceiver(
                arguments = arguments.tail(),
                onMatch = { markCalled() },
                endNoMatch = { },
                receiver = receiver
            ).executeWholeBlock(block)
        }
    }

    fun executeWholeBlock(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        beginParsing()
        block()
        if (!alreadyCalled) endNoMatch()
    }
}

class TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onError: (message: String) -> Unit,
    private val receiver: ExecutionReceiver
) : BaseExecutingArgumentDescriptionReceiver(), TopLevelArgumentDescriptionReceiver<ExecutionReceiver> {
    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        beginParsing()

        return when (val result = parseCommandArgs(parsers.asList(), arguments)) {
            is CommandArgumentParseResult.Success -> {
                val remaining = result.remaining.args

                // Only execute if there are no remaining arguments - users can opt-in to accepting remaining arguments
                // with special argument.
                if (result.isFullMatch()) {
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
        beginParsing()

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            endNoMatch = { onError("No match for command set") },
            onMatch = {},
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        beginParsing()

        SubcommandsExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = {},
            endNoMatch = { onError("no matching subcommand") },
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    private fun <E> reportError(index: Int, error: E) {
        val prefix = "Error while parsing argument $index"
        val message = if (error is ReadableCommandArgumentParseError) "$prefix: ${error.message}" else prefix

        onError(message)
    }
}
