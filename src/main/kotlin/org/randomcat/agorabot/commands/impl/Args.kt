package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.math.BigInteger

interface NamedCommandArgument {
    val name: String
}

interface TypedCommandArgument {
    val type: String
}

abstract class BaseCommandArgument<T>(
    override val name: String,
) : CommandArgumentParser<T, ReadableCommandArgumentParseError>, NamedCommandArgument, TypedCommandArgument {
    protected val noArgumentError =
        CommandArgumentParseResult.Failure(ReadableCommandArgumentParseError("no argument provided"))

    open val count: CommandArgumentUsage.Count get() = CommandArgumentUsage.Count.ONCE

    override fun usage(): CommandArgumentUsage {
        return CommandArgumentUsage(
            name = name,
            type = type,
            count = count,
        )
    }
}

fun IntArg(name: String) = object : BaseCommandArgument<BigInteger>(name) {
    override val type: String
        get() = "int"

    override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<BigInteger, ReadableCommandArgumentParseError> {
        val args = arguments.args
        if (args.isEmpty()) return noArgumentError

        val firstArg = args.first()
        val numberArg = firstArg.toBigIntegerOrNull()

        return if (numberArg != null)
            CommandArgumentParseSuccess(numberArg, arguments.tail())
        else
            CommandArgumentParseFailure(ReadableCommandArgumentParseError("invalid int: $firstArg"))
    }
}

fun LiteralArg(name: String) = object : BaseCommandArgument<Unit>(name) {
    override val type: String
        get() = "literal"

    override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<Unit, ReadableCommandArgumentParseError> {
        val args = arguments.args
        if (args.isEmpty()) return noArgumentError

        if (args.first() != name) {
            return CommandArgumentParseResult.Failure(
                ReadableCommandArgumentParseError("no match for literal $name"),
            )
        }

        return CommandArgumentParseSuccess(Unit, arguments.tail())
    }
}

fun StringArg(name: String) = object : BaseCommandArgument<String>(name) {
    override val type: String
        get() = "string"

    override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<String, ReadableCommandArgumentParseError> {
        val args = arguments.args
        if (args.isEmpty()) return noArgumentError

        return CommandArgumentParseSuccess(args.first(), arguments.tail())
    }
}

fun RemainingStringArgs(name: String) = object : BaseCommandArgument<List<String>>(name) {
    override val type: String
        get() = "string"

    override val count: CommandArgumentUsage.Count
        get() = CommandArgumentUsage.Count.REPEATING

    override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<ImmutableList<String>, ReadableCommandArgumentParseError> {
        return CommandArgumentParseSuccess(arguments.args.toImmutableList(), UnparsedCommandArgs(emptyList()))
    }
}
