package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope
import kotlin.system.exitProcess

class HaltCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        noArgs().requires(InDiscordSimple).permissions(BotScope.admin()) {
            currentJda.shutdownNow()
            exitProcess(0)
        }
    }
}
