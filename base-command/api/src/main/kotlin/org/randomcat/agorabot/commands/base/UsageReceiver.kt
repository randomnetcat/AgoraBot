package org.randomcat.agorabot.commands.base

import kotlinx.collections.immutable.*

private fun CommandArgumentUsage.Count.symbol(): String {
    return when (this) {
        CommandArgumentUsage.Count.ONCE -> ""
        CommandArgumentUsage.Count.OPTIONAL -> "?"
        CommandArgumentUsage.Count.REPEATING -> "..."
    }
}

private fun formatArgumentUsageWrapped(usage: CommandArgumentUsage): String {
    return if (usage.type == LITERAL_ARG_TYPE && usage.count == CommandArgumentUsage.Count.ONCE && usage.name != null) {
        usage.name
    } else {
        "[${usage.name ?: "_"}${usage.type?.let { ": $it" } ?: ""}${usage.count.symbol()}]"
    }
}

private fun formatArgumentUsages(usages: Iterable<CommandArgumentUsage>): String {
    return usages.joinToString(" ") { formatArgumentUsageWrapped(it) }
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

private class MatchFirstUsageArgumentDescriptionReceiver(
    private val receiver: PendingInvocation<Nothing>,
) : ArgumentMultiDescriptionReceiver<Nothing> {
    private val options = mutableListOf<String>()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        options += formatArgumentUsages(parsers.map { it.usage() })
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        block()
    }

    fun options(): ImmutableList<String> {
        return options.toImmutableList()
    }
}

private class UsageSubcommandsArgumentDescriptionReceiver(
    private val receiver: PendingInvocation<Nothing>,
) : SubcommandsArgumentDescriptionReceiver<Nothing> {
    private val checker = SubcommandsReceiverChecker()
    private var options: PersistentList<String> = persistentListOf()

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        checker.checkArgsRaw()
        options = persistentListOf(formatArgumentUsages(parsers.map { it.usage() }))
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        checker.checkMatchFirst()
        options = MatchFirstUsageArgumentDescriptionReceiver(receiver).apply(block).options().toPersistentList()
    }

    override fun subcommand(
        name: String,
        block: SubcommandsArgumentDescriptionReceiver<Nothing>.() -> Unit,
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

class UsageTopLevelArgumentDescriptionReceiver(
    private val receiver: PendingInvocation<Nothing>,
) : TopLevelArgumentDescriptionReceiver<Nothing> {
    private var usageValue: String? = null

    override fun <T, E, R> argsRaw(
        parsers: List<CommandArgumentParser<T, E>>,
        mapParsed: (List<T>) -> R,
    ): PendingInvocation<Nothing> {
        check(usageValue == null)
        usageValue = formatArgumentUsages(parsers.map { it.usage() })
        return receiver
    }

    override fun matchFirst(block: ArgumentMultiDescriptionReceiver<Nothing>.() -> Unit) {
        check(usageValue == null)
        usageValue = formatArgumentSelection(
            MatchFirstUsageArgumentDescriptionReceiver(receiver).apply(block).options()
        )
    }

    override fun subcommands(block: SubcommandsArgumentDescriptionReceiver<Nothing>.() -> Unit) {
        check(usageValue == null)

        usageValue = formatArgumentSelection(
            UsageSubcommandsArgumentDescriptionReceiver(receiver).also(block).options()
        )
    }

    fun usage(): String = checkNotNull(this.usageValue)
}
