package org.randomcat.agorabot.commands.impl

/**
 * Returns [parseResult] if [parseResult] should be considered a successful parse, otherwise returns null.
 */
private fun <T, E> filterParseResult(
    parseResult: CommandArgumentParseResult<T, E>,
): CommandArgumentParseSuccess<T>? {
    return if (parseResult.isFullMatch()) parseResult as CommandArgumentParseSuccess else null
}

private class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver,
) : ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private val flag = ParseOnceFlag()
    private var hasCalled: Boolean = false

    private fun markCalled() {
        hasCalled = true
        onMatch()
    }

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments)) ?: return NullPendingExecutionReceiver
        markCalled()

        return simpleInvokingPendingExecutionReceiver { exec ->
            exec(receiver, mapParsed(parseResult.value))
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        if (hasCalled) return

        // This will correctly handle everything. It will mark as called on the first match, it will not call
        // endNoMatch if nothing matches, and it will use the correct arguments and receiver.
        block()
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        flag.beginParsing()
        block()
        if (!hasCalled) endNoMatch()
    }
}

private class SubcommandsExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver,
) : SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> {
    private val checker = SubcommandsReceiverChecker()
    private val onceFlag = ParseOnceFlag()
    private var hasCalled: Boolean = false

    private fun markCalled() {
        hasCalled = true
        onMatch()
    }

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        checker.checkArgsRaw()

        val parseResult = filterParseResult(parseCommandArgs(parsers, arguments)) ?: return NullPendingExecutionReceiver
        markCalled()

        return simpleInvokingPendingExecutionReceiver { exec ->
            exec(receiver, mapParsed(parseResult.value))
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkMatchFirst()

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = { markCalled() },
            endNoMatch = {},
            receiver = receiver,
        ).executeWholeBlock(block)
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkSubcommand(subcommand = name)
        if (hasCalled) return

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
        if (!hasCalled) endNoMatch()
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
