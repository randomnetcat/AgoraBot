package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.requirements.haltable.Haltable
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.commands.base.requires
import org.randomcat.agorabot.permissions.BotScope

class HaltCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        noArgs().requires(Haltable).permissions(BotScope.admin()) {
            respond("Halting...")
            requirement().scheduleHalt()
        }
    }
}
