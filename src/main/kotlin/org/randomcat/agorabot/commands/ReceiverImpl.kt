package org.randomcat.agorabot.commands

abstract class BaseExecutingArgumentDescriptionReceiver {
    private var alreadyParsed: Boolean = false

    protected fun beginParsing() {
        check(!alreadyParsed)
        alreadyParsed = true
    }
}

class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
    private val arguments: UnparsedCommandArgs,
    private val onMatch: () -> Unit,
    private val endNoMatch: () -> Unit,
    private val receiver: ExecutionReceiver
) : BaseExecutingArgumentDescriptionReceiver(), ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private var _alreadyCalled = false
    private val alreadyCalled get() = _alreadyCalled

    private fun markCalled() {
        _alreadyCalled = true
        onMatch()
    }

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit
    ) {
        if (alreadyCalled) return

        val parseResult = parseCommandArgs(parsers.asList(), arguments)

        if (parseResult is CommandArgumentParseSuccess && parseResult.isFullMatch()) {
            markCalled()
            exec(receiver, parseResult.value)
        }
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        if (alreadyCalled) return

        MatchFirstExecutingArgumentDescriptionReceiver(
            arguments = arguments,
            onMatch = { markCalled() },
            endNoMatch = {},
            receiver
        ).executeWholeBlock(block)
    }

    fun executeWholeBlock(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        beginParsing()

        block()
        if (!_alreadyCalled) endNoMatch()
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

    private fun <E> reportError(index: Int, error: E) {
        val prefix = "Error while parsing argument $index"
        val message = if (error is ReadableCommandArgumentParseError) "$prefix: ${error.message}" else prefix

        onError(message)
    }
}
