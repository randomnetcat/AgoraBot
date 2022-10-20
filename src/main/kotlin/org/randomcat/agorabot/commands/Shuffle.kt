package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.RemainingStringArgs
import org.randomcat.agorabot.util.userFacingRandom

class ShuffleCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(RemainingStringArgs("values")) cmd@{ (values) ->
                if (values.isEmpty()) {
                    respond("No values were provided.")
                    return@cmd
                }

                respond(values.shuffled(userFacingRandom()).joinToString(", ", prefix = "Result: "))
            }
        }
    }
}
