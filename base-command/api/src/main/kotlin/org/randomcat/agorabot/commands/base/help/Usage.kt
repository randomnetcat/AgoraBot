package org.randomcat.agorabot.commands.base.help

import org.randomcat.agorabot.commands.base.CommandArgumentUsage
import org.randomcat.agorabot.commands.base.LITERAL_ARG_TYPE
import org.randomcat.agorabot.commands.base.NO_ARGUMENTS

val CommandArgumentUsage.Count.usageSymbol: String
    get() = when (this) {
        CommandArgumentUsage.Count.ONCE -> ""
        CommandArgumentUsage.Count.OPTIONAL -> "?"
        CommandArgumentUsage.Count.REPEATING -> "..."
    }

private fun formatArgumentUsageWrapped(usage: CommandArgumentUsage): String {
    return if (usage.type == LITERAL_ARG_TYPE && usage.count == CommandArgumentUsage.Count.ONCE && usage.name != null) {
        usage.name
    } else {
        "[${usage.name ?: "_"}${usage.type?.let { ": $it" } ?: ""}${usage.count.usageSymbol}]"
    }
}

private fun formatArgumentUsages(usages: List<CommandArgumentUsage>): String {
    return if (usages.isNotEmpty())
        usages.joinToString(" ") { formatArgumentUsageWrapped(it) }
    else
        ""
}

private fun BaseCommandUsageModel.hasMultipleOptions(): Boolean = when (this) {
    is BaseCommandUsageModel.Subcommands -> {
        subcommandsMap.size > 1 || (subcommandsMap.size == 1 && subcommandsMap.values.single().hasMultipleOptions())
    }

    is BaseCommandUsageModel.MatchArguments -> {
        options.size > 1
    }
}

private fun BaseCommandUsageModel.hasArguments(): Boolean = when (this) {
    is BaseCommandUsageModel.Subcommands -> {
        subcommandsMap.isNotEmpty()
    }

    is BaseCommandUsageModel.MatchArguments -> {
        options.any { it.arguments.isNotEmpty() }
    }
}

fun BaseCommandUsageModel.simpleUsageString(): String {
    return when (this) {
        is BaseCommandUsageModel.Subcommands -> {
            if (subcommandsMap.isNotEmpty())
                subcommandsMap.entries.joinToString(" | ") {
                    if (it.value.hasArguments()) {
                        val subUsage = it.value.simpleUsageString()
                        it.key + " " + if (it.value.hasMultipleOptions()) "[$subUsage]" else subUsage
                    } else {
                        it.key
                    }
                }
            else
                "<no subcommands>"
        }

        is BaseCommandUsageModel.MatchArguments -> {
            options.joinToString(" | ") {
                if (it.arguments.isNotEmpty()) formatArgumentUsages(it.arguments) else NO_ARGUMENTS
            }
        }
    }
}
