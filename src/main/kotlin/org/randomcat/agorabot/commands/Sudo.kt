package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.permissions.BotScope

private val PERMISSION = BotScope.command("sudo")

class SudoCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            noArgs().permissions(PERMISSION) { _ ->
                respond("You are now root.")
            }

            args(RemainingStringArgs("command")) {
                if (senderHasPermission(PERMISSION)) {
                    respond("Done.")
                } else {
                    respond("This incident has been reported.")
                }
            }
        }
    }
}
