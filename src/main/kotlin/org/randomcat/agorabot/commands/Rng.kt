package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.util.userFacingRandom
import java.math.BigInteger
import kotlin.random.asJavaRandom

class RngCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(IntArg("max")) cmd@{ (max) ->
                doResponse(min = BigInteger.ONE, max = max)
            }

            args(IntArg("min"), IntArg("max")) cmd@{ (min, max) ->
                doResponse(min = min, max = max)
            }
        }
    }

    private suspend fun BaseCommandExecutionReceiver.doResponse(min: BigInteger, max: BigInteger) {
        if (min > max) {
            respond("Max value must be at least as big as min value.")
            return
        }

        val length = (max - min) + BigInteger.ONE
        var offset: BigInteger

        do {
            offset = BigInteger(length.bitLength(), userFacingRandom().asJavaRandom())
        } while (offset >= length)

        respond("Random number from $min to $max: ${min + offset}")
    }
}
