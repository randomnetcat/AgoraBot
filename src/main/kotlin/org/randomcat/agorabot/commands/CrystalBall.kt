package org.randomcat.agorabot.commands

private val RESPONSES = listOf(
    "TRUE",
    "FALSE",
    "DISMISS",
    "IRRELEVANT",
    "SHENANIGANS",
    "I GUESS",
    "PROBABLY",
    "WHO KNOWS",
    "OH GOD, DEFINITELY NOT",
    "DON'T ASK ME",
    "THE ONLY REAL ARGUMENT IN FAVOR OF THIS IS WISHFUL THINKING",
    "A TYPICAL EXAMPLE OF \"I SAY I DO, THEREFORE I DO\" WHICH HAS PLAGUED AGORA FOR A LONG TIME",
    "IF YOU SAY SO",
    "EDIBLE",
    "INEDIBLE",
)

class CrystalBallCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
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
