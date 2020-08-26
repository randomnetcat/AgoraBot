package org.randomcat.agorabot.commands

import kotlin.random.Random
import kotlin.random.nextInt

class RngCommand : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            args(IntArg("min"), IntArg("max"), NoMoreArgs) { args ->
                val min = args.first
                val max = args.second
                doResponse(min = min, max = max)
            }

            args(IntArg("max"), NoMoreArgs) { args ->
                val max = args.first
                doResponse(min = 1, max = max)
            }
        }
    }

    private fun ExecutionReceiverImpl.doResponse(min: Int, max: Int) {
        val result = Random.nextInt(min..max)

        respond("Random number from $min to $max: $result")
    }
}
