package org.randomcat.agorabot.commands

interface NamedCommandArgument {
    val name: String
}

interface TypedCommandArgument {
    val type: String
}

abstract class BaseCommandArgument<T>(
    override val name: String
) : CommandArgumentParser<T, ReadableCommandArgumentParseError>, NamedCommandArgument, TypedCommandArgument {
    protected val noArgumentError =
        CommandArgumentParseResult.Failure(ReadableCommandArgumentParseError("no argument provided"))
}

fun IntArg(name: String) = object : BaseCommandArgument<Int>(name) {
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

fun StringArg(name: String) = object : BaseCommandArgument<String>(name) {
    override val type: String
        get() = "string"

    override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<String, ReadableCommandArgumentParseError> {
        val args = arguments.args
        if (args.isEmpty()) return noArgumentError

        return CommandArgumentParseSuccess(args.first(), arguments.tail())
    }
}
