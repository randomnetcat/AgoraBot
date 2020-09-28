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
        matchFirst {
            noArgs { _ ->
                respond("Judged ${RESPONSES.random()}.")
            }

            args(StringArg("statement")) { (statement) ->
                respond("\"$statement\" judged ${RESPONSES.random()}.")
            }
        }
    }
}
