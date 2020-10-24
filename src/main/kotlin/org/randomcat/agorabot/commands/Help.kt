package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.QueryableCommandRegistry

private fun MessageBuilder.appendUsage(name: String, command: Command) {
    val usageHelp =
        if (command is BaseCommand)
            command.usage().ifBlank { NO_ARGUMENTS }
        else
            "<no usage available>"

    append(name, MessageBuilder.Formatting.BOLD)
    append(": $usageHelp")
}

class HelpCommand(
    strategy: BaseCommandStrategy,
    private val registry: QueryableCommandRegistry,
) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            noArgs {
                val commands = registry.commands()
                val builder = MessageBuilder()

                for ((name, command) in commands) {
                    builder.appendUsage(name = name, command = command)
                    builder.appendLine()
                }

                if (!builder.isEmpty) {
                    builder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { respond(it) }
                }
            }

            args(StringArg("command")) { (commandName) ->
                val commands = registry.commands()

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
