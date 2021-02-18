package org.randomcat.agorabot.commands.impl

import kotlinx.collections.immutable.*

private fun CommandArgumentUsage.Count.symbol(): String {
    return when (this) {
        CommandArgumentUsage.Count.ONCE -> ""
        CommandArgumentUsage.Count.OPTIONAL -> "?"
        CommandArgumentUsage.Count.REPEATING -> "..."
    }
}

private fun formatArgumentUsage(usage: CommandArgumentUsage): String {
    return "${usage.name ?: "_"}${usage.type?.let { ": $it" } ?: ""}${usage.count.symbol()}"
}

private fun formatArgumentUsages(usages: Iterable<CommandArgumentUsage>): String {
    return usages.joinToString(" ") { "[${formatArgumentUsage(it)}]" }
}

private fun formatArgumentSelection(options: List<String>): String {
    return when (options.size) {
        0 -> ""
        1 -> options.single()
        else -> options.joinToString(" | ") {
            when {
                it.isEmpty() -> NO_ARGUMENTS
                else -> it
            }
        }
    }
}

private class MatchFirstUsageArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>(
    private val receiver: ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Nothing, ArgsExtend>,
) : ArgumentMultiDescriptionReceiver<ExecutionReceiver, ArgsExtend> {
    private val options = mutableListOf<String>()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, ArgsExtend> {
        options += formatArgumentUsages(parsers.map { it.usage() })
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit) {
        block()
    }

    fun options(): ImmutableList<String> {
        return options.toImmutableList()
    }
}

private class UsageSubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>(
    private val receiver: ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Nothing, ArgsExtend>,
) : SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend> {
    private val checker = SubcommandsReceiverChecker()
    private var options: PersistentList<String> = persistentListOf()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, ArgsExtend> {
        checker.checkArgsRaw()
        options = persistentListOf(formatArgumentUsages(parsers.map { it.usage() }))
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit) {
        checker.checkMatchFirst()
        options = MatchFirstUsageArgumentDescriptionReceiver(receiver).apply(block).options().toPersistentList()
    }

    override fun subcommand(
        name: String,
        block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit,
    ) {
        checker.checkSubcommand(subcommand = name)

        val subOptions = UsageSubcommandsArgumentDescriptionReceiver(receiver).apply(block).options()

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

        options = options.add(newOption)
    }

    fun options(): ImmutableList<String> {
        return options
    }
}

class UsageTopLevelArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>(
    private val receiver: ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, Nothing, ArgsExtend>,
) : TopLevelArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend> {
    private var usageValue: String? = null

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): ExtendableArgumentPendingExecutionReceiver<ExecutionReceiver, R, ArgsExtend> {
        check(usageValue == null)
        usageValue = formatArgumentUsages(parsers.map { it.usage() })
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit) {
        check(usageValue == null)
        usageValue = formatArgumentSelection(
            MatchFirstUsageArgumentDescriptionReceiver(receiver).apply(block).options()
        )
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<ExecutionReceiver, ArgsExtend>.() -> Unit) {
        check(usageValue == null)

        usageValue = formatArgumentSelection(
            UsageSubcommandsArgumentDescriptionReceiver(receiver).also(block).options()
        )
    }

    fun usage(): String = checkNotNull(this.usageValue)
}
