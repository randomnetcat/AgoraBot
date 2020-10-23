package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*

class SudoCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        args(RemainingStringArgs("command")) {
            respond("This incident has been reported.")
        }
    }
}
