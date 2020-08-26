package org.randomcat.agorabot.commands

sealed class CommandArgumentParseResult<out T, out E> {
    data class Failure<out E>(
        val error: E,
    ) : CommandArgumentParseResult<Nothing, E>()

    data class Success<out T>(
        val value: T,
        val remaining: UnparsedCommandArgs,
    ) : CommandArgumentParseResult<T, Nothing>()
}

typealias CommandArgumentParseSuccess<T> = CommandArgumentParseResult.Success<T>
typealias CommandArgumentParseFailure<E> = CommandArgumentParseResult.Failure<E>

interface CommandArgumentParser<out T, out E> {
    fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<T, E>
}

data class SequentialArgumentParseFailure<E>(val index: Int, val error: E)

fun <T, E> parseCommandArgs(
    parsers: Iterable<CommandArgumentParser<T, E>>,
    arguments: UnparsedCommandArgs
): CommandArgumentParseResult<List<T>, SequentialArgumentParseFailure<E>> {
    var remainingArgs = arguments
    val results = mutableListOf<T>()

    parsers.forEachIndexed { index, parser ->
        @Suppress("Unused")
        val ensureExhaustive = when (val parseResult = parser.parse(remainingArgs)) {
            is CommandArgumentParseResult.Success -> {
                remainingArgs = parseResult.remaining
                results.add(parseResult.value)
            }

            is CommandArgumentParseResult.Failure -> {
                return CommandArgumentParseResult.Failure(SequentialArgumentParseFailure(index, parseResult.error))
            }
        }
    }

    return CommandArgumentParseResult.Success(results, remainingArgs)
}
