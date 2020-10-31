package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
            it.isEmpty() -> NO_ARGUMENTS
            else -> it
        }
    }
}

private class MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver> :
    ArgumentMultiDescriptionReceiver<ExecutionReceiver> {
    private val options = mutableListOf<String>()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        options += formatArgumentUsages(parsers.map { it.usage() })
        return NullPendingExecutionReceiver
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
    private val checker = SubcommandsReceiverChecker()
    private var options: ImmutableList<String> = persistentListOf()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        checker.checkArgsRaw()
        options = persistentListOf(formatArgumentUsages(parsers.map { it.usage() }))
        return NullPendingExecutionReceiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkMatchFirst()
        options =
            MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver>()
                .apply(block)
                .options()
    }

    override fun subcommand(name: String, block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver>.() -> Unit) {
        checker.checkSubcommand(subcommand = name)

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

        options = (options + newOption).toImmutableList()
    }

    fun options(): ImmutableList<String> {
        return options
    }
}

class UsageTopLevelArgumentDescriptionReceiver<ExecutionReceiver> :
    TopLevelArgumentDescriptionReceiver<ExecutionReceiver> {
    private var usageValue: String? = null

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ArgumentPendingExecutionReceiver<ExecutionReceiver, R> {
        check(usageValue == null)
        usageValue = formatArgumentUsages(parsers.map { it.usage() })
        return NullPendingExecutionReceiver
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
