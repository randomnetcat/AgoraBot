package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.BaseCommand
import org.randomcat.agorabot.commands.impl.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository

class SecretHitlerCommand(
    strategy: BaseCommandStrategy,
    private val repository: SecretHitlerRepository,
) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        subcommands {
        }
    }
}
