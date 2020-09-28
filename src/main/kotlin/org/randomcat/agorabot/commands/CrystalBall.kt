package org.randomcat.agorabot.commands

private val RESPONSES = listOf(
    "TRUE",
    "FALSE",
    "DISMISS",
    "IRRELEVANT",
    "SHENANIGANS",
)

class CrystalBallCommand : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        noArgs {
            respond("Judged ${RESPONSES.random()}.")
        }
    }
}
