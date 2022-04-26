package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.requirements.discord.InDiscordSimple
import org.randomcat.agorabot.commands.base.requirements.discord.currentChannel
import org.randomcat.agorabot.commands.base.requirements.discord.currentMessageEvent
import org.randomcat.agorabot.commands.base.requires

class DuckCommand(
    strategy: BaseCommandStrategy,
    private val addListener: (ListeningSpec) -> Unit,
) : BaseCommand(strategy) {
    data class ListeningSpec(
        private val channelId: String,
        private val userId: String,
    )

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("start") {
                noArgs().requires(InDiscordSimple) {
                    val listeningSpec = ListeningSpec(
                        channelId = currentChannel.id,
                        userId = currentMessageEvent.author.id,
                    )

                    addListener(listeningSpec)

                    respond("Alright, I'm listening. Type \"I fixed it\" to stop.")
                }
            }
        }
    }
}
