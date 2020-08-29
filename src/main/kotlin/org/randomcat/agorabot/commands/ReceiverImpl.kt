package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

abstract class BaseExecutingArgumentDescriptionReceiver {
    private var alreadyParsed: Boolean = false

    protected fun beginParsing() {
        check(!alreadyParsed)
        alreadyParsed = true
    }
}

private abstract class BaseExecutingNestedArgumentDescriptionReceiver<ExecutionReceiver>
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

private class MatchFirstExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
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

private class SubcommandsExecutingArgumentDescriptionReceiver<ExecutionReceiver>(
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

private fun countSymbol(count: CommandArgumentUsage.Count): String {
    return when (count) {
        CommandArgumentUsage.Count.ONCE -> ""
        CommandArgumentUsage.Count.OPTIONAL -> "?"
        CommandArgumentUsage.Count.REPEATING -> "..."
    }
}

private fun formatArgumentUsage(usage: CommandArgumentUsage): String {
    return "${usage.name ?: "_"}${if (usage.type != null) ": " + usage.type else ""}${countSymbol(usage.count)}"
}

private fun formatArgumentUsages(usages: Iterable<CommandArgumentUsage>): String {
    return usages.joinToString(" ") { "[${formatArgumentUsage(it)}]" }
}

private fun formatArgumentSelection(options: List<String>): String {
    if (options.size == 1) return options.single()

    return options.joinToString(" | ") {
        when {
            it.isEmpty() -> "<no args>"
            else -> it
        }
    }
}

private class MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver> :
    ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private val options = mutableListOf<String>()

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit,
    ) {
        options += formatArgumentUsages(parsers.map { it.usage() })
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        block()
    }

    fun options(): ImmutableList<String> {
        return options.toImmutableList()
    }
}

private class UsageSubcommandsArgumentDescriptionReceiver<ExecutionReceiver>
    : SubcommandsArgumentDescriptionReceiver<ExecutionReceiver> {
    private var options: List<String>? = null

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit,
    ) {
        check(options == null)
        options = listOf(formatArgumentUsages(parsers.map { it.usage() }))
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(options == null)
        options =
            MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver>()
                .apply(block)
                .options()
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        val subOptions =
            UsageSubcommandsArgumentDescriptionReceiver<ExecutionReceiver>()
                .apply(block)
                .options()

        val newOption = name + when {
            subOptions.isEmpty() -> ""
            subOptions.size == 1 -> {
                val subOption = subOptions.single()
                if (subOption.isEmpty())
                    ""
                else
                    " " + subOptions.single()
            }
            else -> " [${formatArgumentSelection(subOptions)}]"
        }

        options = (options ?: emptyList()) + newOption
    }

    fun options(): ImmutableList<String> {
        return checkNotNull(options).toImmutableList()
    }
}

class UsageTopLevelArgumentDescriptionReceiver<ExecutionReceiver> :
    TopLevelArgumentDescriptionReceiver<ExecutionReceiver> {
    private var usageValue: String? = null

    override fun <T, E> argsRaw(
        vararg parsers: CommandArgumentParser<T, E>,
        exec: ExecutionReceiver.(args: List<T>) -> Unit,
    ) {
        check(usageValue == null)
        usageValue = formatArgumentUsages(parsers.map { it.usage() })
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(usageValue == null)
        usageValue = formatArgumentSelection(
            MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver>().apply(block).options()
        )
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        check(usageValue == null)

        usageValue = formatArgumentSelection(
            UsageSubcommandsArgumentDescriptionReceiver<ExecutionReceiver>()
                .also(block)
                .options()
        )
    }

    fun usage(): String = checkNotNull(this.usageValue)
}
