package org.randomcat.agorabot.commands

import org.randomcat.agorabot.MutableGuildPrefixMap

class PrefixCommand(private val prefixMap: MutableGuildPrefixMap) : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            noArgs {
                respond("The prefix is: ${prefixMap.prefixForGuild(currentGuildId())}")
            }

            args(StringArg("new_prefix")) { args ->
                val newPrefix = args.first
                prefixMap.setPrefixForGuild(currentGuildId(), newPrefix)
                respond("The prefix has been updated.")
            }
        }
    }
}
