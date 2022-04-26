package org.randomcat.agorabot.commands.base.help

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.randomcat.agorabot.commands.base.CommandArgumentUsage

data class BaseCommandArgumentSet(
    val arguments: ImmutableList<CommandArgumentUsage>,
    val help: String? = null,
)

sealed class BaseCommandUsageModel {
    abstract val overallHelp: String?

    data class Subcommands(
        val subcommandsMap: ImmutableMap<String, BaseCommandUsageModel>,
        override val overallHelp: String? = null,
    ) : BaseCommandUsageModel()

    data class MatchArguments(
        val options: ImmutableList<BaseCommandArgumentSet>,
        override val overallHelp: String? = null,
    ) : BaseCommandUsageModel()
}
