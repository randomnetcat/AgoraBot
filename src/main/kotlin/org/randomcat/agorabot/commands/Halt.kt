package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requires
import org.randomcat.agorabot.commands.impl.InDiscordSimple
import org.randomcat.agorabot.commands.impl.currentJda
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
