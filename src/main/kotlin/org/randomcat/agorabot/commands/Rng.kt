package org.randomcat.agorabot.commands

import kotlin.random.Random
import kotlin.random.nextInt

class RngCommand : ChatCommand() {
    override fun ArgumentDescriptionReceiver<ExecutionReceiverImpl>.impl() {
        args(IntArg("min"), IntArg("max")) { args ->
            val min = args.first
            val max = args.second
            val result = Random.nextInt(min..max)

            respond("Random number from $min to $max: $result")
        }
    }
}
