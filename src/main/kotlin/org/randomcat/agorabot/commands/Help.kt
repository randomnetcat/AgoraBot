package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.help.BaseCommandUsageModel
import org.randomcat.agorabot.commands.base.help.concatWrappedArgumentUsages
import org.randomcat.agorabot.commands.base.help.simpleUsageString
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.QueryableCommandRegistry

private fun MessageBuilder.appendUsage(name: String, command: Command) {
    val usageHelp =
        if (command is BaseCommand)
            command.usage().simpleUsageString().ifBlank { NO_ARGUMENTS }
        else
            "<no usage available>"

    append(name, MessageBuilder.Formatting.BOLD)
    append(": $usageHelp")
}

private const val HELP_INDENT = "  "

private data class UsageGenerationConfig(
    val includeOptionHelp: Boolean,
)

private fun StringBuilder.doMatchUsage(
    usage: BaseCommandUsageModel.MatchArguments,
    commandPrefix: String,
    config: UsageGenerationConfig,
) {
    usage.options.forEach { option ->
        appendLine(commandPrefix + " " + concatWrappedArgumentUsages(option.arguments).ifBlank { NO_ARGUMENTS })

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

                commands().filter { (name, _) -> !suppressedCommands.contains(name) }.forEach { (name, command) ->
                    builder.appendUsage(name = name, command = command)
                    builder.appendLine()
                }

                if (!builder.isEmpty) {
                    builder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { respond(it) }
                }
            }

            args(StringArg("command")) { (commandName) ->
                val commands = commands()

                if (commands.containsKey(commandName)) {
                    val command = commands.getValue(commandName)

                    if (command !is BaseCommand) {
                        respond("No help is available for this command.")
                        return@args
                    }

                    val usage = command.usage()

                    val usageString = buildString {
                        val includeOptionHelp = when (usage) {
                            is BaseCommandUsageModel.MatchArguments -> {
                                usage.options.any { it.help != null }
                            }

                            else -> true
                        }

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
