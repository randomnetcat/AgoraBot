package org.randomcat.agorabot.commands.base

sealed class CommandArgumentParseResult<out T, out E> {
    data class Failure<out E>(
        val error: E,
    ) : CommandArgumentParseResult<Nothing, E>()

    data class Success<out T>(
        val value: T,
        val remaining: UnparsedCommandArgs,
    ) : CommandArgumentParseResult<T, Nothing>()

    fun isSuccess() = this is Success
    fun isFailure() = this is Failure

    fun isFullMatch() = this is Success && remaining.args.isEmpty()
}

typealias CommandArgumentParseSuccess<T> = CommandArgumentParseResult.Success<T>
typealias CommandArgumentParseFailure<E> = CommandArgumentParseResult.Failure<E>

interface CommandArgumentParser<out T, out E> {
    fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<T, E>
    fun usage(): CommandArgumentUsage
}

data class CommandArgumentUsage(
    val name: String?,
    val type: String?,
    val count: Count,
) {
    enum class Count {
        ONCE,
        OPTIONAL,
        REPEATING,
    }
}

data class SequentialArgumentParseFailure<E>(val index: Int, val error: E)

interface ReadableCommandArgumentParseError {
    val message: String
}

fun ReadableCommandArgumentParseError(msg: String) = object : ReadableCommandArgumentParseError {
    override val message: String
        get() = msg
}

fun <T, E> parseCommandArgs(
    parsers: Iterable<CommandArgumentParser<T, E>>,
    arguments: UnparsedCommandArgs
): CommandArgumentParseResult<List<T>, SequentialArgumentParseFailure<E>> {
    var remainingArgs = arguments
    val results = mutableListOf<T>()

    parsers.forEachIndexed { index, parser ->
        @Suppress("UNUSED_VARIABLE")
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
