package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.MessageBuilder
import org.randomcat.agorabot.QueryableCommandRegistry

class HelpCommand(private val registry: QueryableCommandRegistry) : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        noArgs {
            val commands = registry.commands()
            val builder = MessageBuilder()

            for ((name, command) in commands) {
                val usageHelp =
                    if (command is BaseCommand)
                        command.usage()
                    else
                        "<no usage available>"

                builder.append(name, MessageBuilder.Formatting.BOLD)
                builder.append(": $usageHelp")
                builder.appendLine()
            }

            if (!builder.isEmpty) {
                builder.buildAll(MessageBuilder.SplitPolicy.NEWLINE).forEach { respond(it) }
            }
        }
    }
}
