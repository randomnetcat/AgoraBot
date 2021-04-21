package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*

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
                noArgs().requiresDiscord {
                    val listeningSpec = ListeningSpec(
                        channelId = currentChannel().id,
                        userId = currentMessageEvent().author.id,
                    )

                    addListener(listeningSpec)

                    respond("Alright, I'm listening. Type \"I fixed it\" to stop.")
                }
            }
        }
    }
}
