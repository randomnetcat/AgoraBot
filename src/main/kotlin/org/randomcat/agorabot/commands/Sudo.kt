package org.randomcat.agorabot.commands

class SudoCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        args(RemainingStringArgs("command")) {
            respond("This incident has been reported.")
        }
    }
}
