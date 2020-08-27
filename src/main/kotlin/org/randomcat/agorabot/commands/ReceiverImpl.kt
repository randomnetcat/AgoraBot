package org.randomcat.agorabot.commands

class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
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

class TopLevelExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
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
