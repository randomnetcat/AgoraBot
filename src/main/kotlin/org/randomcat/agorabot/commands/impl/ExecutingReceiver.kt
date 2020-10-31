package org.randomcat.agorabot.commands.impl


private abstract class BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>
    : ArgumentDescriptionReceiver<ExecutionReceiver> {
    protected abstract val onMatch: () -> Unit
    protected abstract val arguments: UnparsedCommandArgs
    protected abstract val receiver: ExecutionReceiver

    private var _alreadyCalled = false
    protected val alreadyCalled get() = _alreadyCalled

    protected fun markCalled() {
        _alreadyCalled = true
        onMatch()
    }

    protected fun <T, E, R> doArgsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
        exec: ExecutionReceiver.(arg: R) -> Unit,
    ) {
        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments))

        if (parseResult != null) {
            markCalled()
            exec(receiver, mapParsed(parseResult.value))
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
            parseResult: CommandArgumentParseResult<T, E>,
        ): CommandArgumentParseSuccess<T>? {
            return if (parseResult.isFullMatch()) parseResult as CommandArgumentParseSuccess else null
        }
    }
}

private class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    override val arguments: UnparsedCommandArgs,
    override val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    override val receiver: ExecutionReceiver,
) : BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>(),
    ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private val flag = ParseOnceFlag()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        val safeParsers = parsers.toList()

        return simpleInvokingPendingExecutionReceiver { exec ->
            if (!alreadyCalled) doArgsRaw(safeParsers, mapParsed, exec)
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        if (alreadyCalled) return
        doMatchFirst(block)
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        flag.beginParsing()
        block()
        if (!alreadyCalled) endNoMatch()
    }
}

private class SubcommandsExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    override val arguments: UnparsedCommandArgs,
    override val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    override val receiver: ExecutionReceiver,
) : BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>(),
    SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> {
    private val checker = SubcommandsReceiverChecker()
    private val onceFlag = ParseOnceFlag()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        val safeParsers = parsers.toList()

        return simpleInvokingPendingExecutionReceiver { exec ->
            checker.checkArgsRaw()
            doArgsRaw(safeParsers, mapParsed, exec)
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkMatchFirst()
        doMatchFirst(block)
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkSubcommand(subcommand = name)
        if (alreadyCalled) return

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
        checker.reset()
        onceFlag.beginParsing()
        block()
        if (!alreadyCalled) endNoMatch()
    }
}

class TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onError: (message: String) -> Unit,
    private val receiver: ExecutionReceiver,
) : TopLevelArgumentDescriptionReceiver<ExecutionReceiver> {
    private val flag = ParseOnceFlag()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        flag.beginParsing()

        val safeParsers = parsers.toList()

        return simpleInvokingPendingExecutionReceiver { exec ->
            when (val result = parseCommandArgs(safeParsers, arguments)) {
                is CommandArgumentParseResult.Success -> {
                    val remaining = result.remaining.args

                    // Only execute if there are no remaining arguments - users can opt-in to accepting remaining arguments
                    // with special argument.
                    if (result.isFullMatch()) {
                        exec(receiver, mapParsed(result.value))
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
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        flag.beginParsing()

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            endNoMatch = { onError("No match for command set") },
            onMatch = {},
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        flag.beginParsing()

        SubcommandsExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = {},
            endNoMatch = { onError("No matching subcommand") },
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    private fun <E> reportError(index: Int, error: E) {
        val prefix = "Error while parsing argument $index"
        val message = if (error is ReadableCommandArgumentParseError) "$prefix: ${error.message}" else prefix

        onError(message)
    }
}
