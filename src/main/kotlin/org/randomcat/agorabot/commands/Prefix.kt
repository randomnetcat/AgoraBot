package org.randomcat.agorabot.commands

import org.randomcat.agorabot.MutableGuildPrefixMap
import org.randomcat.agorabot.commands.impl.*

class PrefixCommand(
    strategy: BaseCommandStrategy,
    private val prefixMap: MutableGuildPrefixMap,
) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            noArgs {
                respond("The prefix is: ${prefixMap.prefixForGuild(currentGuildId())}")
            }

            args(StringArg("new_prefix")) { (newPrefix) ->
                if (newPrefix.isBlank()) {
                    respond("The prefix cannot be empty. Stop it.")
                    return@args
                }

                prefixMap.setPrefixForGuild(currentGuildId(), newPrefix)
                respond("The prefix has been updated.")
            }
        }
    }
}
