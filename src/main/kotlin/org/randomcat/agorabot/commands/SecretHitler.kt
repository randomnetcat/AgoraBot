package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.BaseCommand
import org.randomcat.agorabot.commands.impl.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy

class SecretHitlerCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
        }
    }
}
