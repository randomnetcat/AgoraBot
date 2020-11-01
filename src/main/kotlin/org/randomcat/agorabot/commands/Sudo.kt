package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope

class SudoCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            noArgs().permissions(BotScope.command("sudo")) { _ ->
                respond("You are now root.")
            }

            args(RemainingStringArgs("command")) {
                respond("This incident has been reported.")
            }
        }
    }
}
