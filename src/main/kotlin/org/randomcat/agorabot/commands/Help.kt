package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.base.*
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

                    val builder = MessageBuilder()
                    builder.appendUsage(name = commandName, command = command)
                    builder.appendLine()

                    respond(builder.build())
                } else {
                    respond("No such command \"$commandName\".")
                }
            }
        }
    }
}
