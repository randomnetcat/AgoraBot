package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope
import kotlin.system.exitProcess

class StopCommand(
    strategy: BaseCommandStrategy,
    private val writeChannelFun: (channelId: String) -> Unit,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        noArgs().requiresDiscord().permissions(BotScope.admin()) {
            try {
                writeChannelFun(currentChannelId)
            } finally {
                currentJda.shutdownNow()
                exitProcess(0)
            }
        }
    }
}
