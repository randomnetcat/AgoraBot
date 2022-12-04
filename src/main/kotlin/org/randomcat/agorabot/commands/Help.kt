package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.StringArg
import org.randomcat.agorabot.commands.base.help.BaseCommandUsageModel
import org.randomcat.agorabot.commands.base.help.concatWrappedArgumentUsages
import org.randomcat.agorabot.listener.QueryableCommandRegistry

private const val HELP_INDENT = "  "

private data class UsageGenerationConfig(
    val includeOptionHelp: Boolean,
)

private fun BaseCommandUsageModel.hasAnyOptionHelp(): Boolean {
    return when (this) {
        is BaseCommandUsageModel.MatchArguments -> options.any { it.help != null }
        is BaseCommandUsageModel.Subcommands -> subcommandsMap.values.any { it.hasAnyOptionHelp() }
    }
}

private fun StringBuilder.doMatchUsage(
    usage: BaseCommandUsageModel.MatchArguments,
    commandPrefix: String,
    config: UsageGenerationConfig,
) {
    usage.options.forEach { option ->
        appendLine(commandPrefix + " " + concatWrappedArgumentUsages(option.arguments))

        if (config.includeOptionHelp) {
            if (option.help != null) {
                appendLine(HELP_INDENT + option.help)
            }

            appendLine()
        }
    }
}

private fun StringBuilder.doSubcommandsUsage(
    usage: BaseCommandUsageModel.Subcommands,
    commandPrefix: String,
    config: UsageGenerationConfig,
) {
    usage.subcommandsMap.forEach { (subcommandName, subcommandUsage) ->
        doUsage(subcommandUsage, "$commandPrefix $subcommandName", config)
        appendLine()
    }
}

private fun StringBuilder.doUsage(usage: BaseCommandUsageModel, commandPrefix: String, config: UsageGenerationConfig) {
    if (usage.overallHelp != null) {
        appendLine("$commandPrefix [...]")
        appendLine(HELP_INDENT + usage.overallHelp)
        appendLine()
    }

    when (usage) {
        is BaseCommandUsageModel.MatchArguments -> {
            doMatchUsage(usage, commandPrefix = commandPrefix, config = config)
        }

        is BaseCommandUsageModel.Subcommands -> {
            doSubcommandsUsage(usage, commandPrefix = commandPrefix, config = config)
        }
    }
}

class HelpCommand(
    strategy: BaseCommandStrategy,
    private val registryFun: () -> QueryableCommandRegistry,
    private val suppressedCommands: ImmutableList<String>,
) : BaseCommand(strategy) {
    constructor(
        strategy: BaseCommandStrategy,
        registryFun: () -> QueryableCommandRegistry,
        suppressedCommands: List<String>,
    ) : this(
        strategy = strategy,
        registryFun = registryFun,
        suppressedCommands = suppressedCommands.toImmutableList(),
    )

    private fun commands() = registryFun().commands()

    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            noArgs {
                val builder = MessageBuilder()

                builder.appendLine("Use help [command] for specific usage. Available commands:")

                commands()
                    .filter { (name, _) -> !suppressedCommands.contains(name) }
                    .entries
                    .sortedBy { it.key }
                    .forEach { (name, command) ->
                        val helpPart = (command as? BaseCommand)?.usage()?.overallHelp?.let { ": $it" } ?: ""
                        builder.append("**$name**").append(helpPart)
                        builder.appendLine()
                    }

                if (!builder.isEmpty) {
                    builder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { respond(it) }
                }
            }

            args(StringArg("command")) cmd@{ (commandName) ->
                val commands = commands()

                if (commands.containsKey(commandName)) {
                    val command = commands.getValue(commandName)

                    if (command !is BaseCommand) {
                        respond("No help is available for this command.")
                        return@cmd
                    }

                    val usage = command.usage()

                    val usageString = buildString {
                        val includeOptionHelp = usage.hasAnyOptionHelp()

                        doUsage(
                            usage = usage,
                            commandPrefix = commandName,
                            config = UsageGenerationConfig(
                                includeOptionHelp = includeOptionHelp,
                            ),
                        )
                    }

                    respond(MessageBuilder().appendCodeBlock(usageString, "").build())
                } else {
                    respond("No such command \"$commandName\".")
                }
            }
        }
    }
}
