package org.randomcat.agorabot.commands

import kotlin.random.Random
import kotlin.random.nextInt

class RngCommand : ChatCommand() {
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        matchFirst {
            args(IntArg("min"), IntArg("max")) { args ->
                val min = args.first
                val max = args.second
                doResponse(min = min, max = max)
            }

            args(IntArg("max")) { args ->
                val max = args.first
                doResponse(min = 1, max = max)
            }
        }
    }

    private fun ExecutionReceiverImpl.doResponse(min: Int, max: Int) {
        if (min >= max) {
            respond("Max value must be at least as big as min value.")
            return
        }

        val result = Random.nextInt(min..max)

        respond("Random number from $min to $max: $result")
    }
}
