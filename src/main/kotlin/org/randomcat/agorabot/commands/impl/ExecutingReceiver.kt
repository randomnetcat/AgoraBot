package org.randomcat.agorabot.commands.impl

/**
 * Returns [parseResult] if [parseResult] should be considered a successful parse, otherwise returns null.
 */
private fun <T, E> filterParseResult(
    parseResult: CommandArgumentParseResult<T, E>,
): CommandArgumentParseSuccess<T>? {
    return if (parseResult.isFullMatch()) parseResult as CommandArgumentParseSuccess else null
}

private class CallOnceFlag(private val onCall: () -> Unit) {
    private var _hasCalled: Boolean = false

    public fun hasCalled(): Boolean = _hasCalled

    fun markCalled() {
        check(!hasCalled())
        _hasCalled = true
        onCall()
    }
}

private class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver,
) : ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private val parseFlag = ParseOnceFlag()
    private val callFlag = CallOnceFlag(onCall = onMatch)

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        if (callFlag.hasCalled()) return NullPendingExecutionReceiver

        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments)) ?: return NullPendingExecutionReceiver
        callFlag.markCalled()

        return simpleInvokingPendingExecutionReceiver { exec ->
            exec(receiver, mapParsed(parseResult.value))
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        if (callFlag.hasCalled()) return

        // This will correctly handle everything. It will mark as called on the first match, it will not call
        // endNoMatch if nothing matches, and it will use the correct arguments and receiver.
        block()
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        parseFlag.beginParsing()
        block()
        if (!callFlag.hasCalled()) endNoMatch()
    }
}

private class SubcommandsExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver,
) : SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> {
    private val checker = SubcommandsReceiverChecker()
    private val parseFlag = ParseOnceFlag()
    private val callFlag = CallOnceFlag(onCall = onMatch)

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        checker.checkArgsRaw()

        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments)) ?: return NullPendingExecutionReceiver
        callFlag.markCalled()

        return simpleInvokingPendingExecutionReceiver { exec ->
            exec(receiver, mapParsed(parseResult.value))
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkMatchFirst()

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = { callFlag.markCalled() },
            endNoMatch = {},
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkSubcommand(subcommand = name)
        if (callFlag.hasCalled()) return

        val argsList = arguments.args
        if (argsList.isEmpty()) return

        val firstArg = argsList.first()

        if (firstArg.equals(name, ignoreCase = true)) {
            SubcommandsExecutingArgumentDescriptionReceiver(
                arguments = arguments.tail(),
                onMatch = { callFlag.markCalled() },
                endNoMatch = { },
                receiver = receiver
            ).executeWholeBlock(block)
        }
    }

    fun executeWholeBlock(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.reset()
        parseFlag.beginParsing()
        block()
        if (!callFlag.hasCalled()) endNoMatch()
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

        when (val result = parseCommandArgs(parsers, arguments)) {
            is CommandArgumentParseResult.Success -> {
                val remaining = result.remaining.args

                // Only execute if there are no remaining arguments - users can opt-in to accepting remaining arguments
                // with special argument.
                if (result.isFullMatch()) {
                    return simpleInvokingPendingExecutionReceiver { exec -> exec(receiver, mapParsed(result.value)) }
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

        return NullPendingExecutionReceiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        flag.beginParsing()

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = {},
            endNoMatch = { onError("No match for command set") },
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
