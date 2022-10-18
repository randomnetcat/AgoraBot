package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.asJavaRandom

private fun BigInteger.toLongOrNull(): Long? {
    return if (this > Long.MAX_VALUE.toBigInteger() || this < Long.MIN_VALUE.toBigInteger())
        null
    else
        longValueExact()
}

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
            offset = BigInteger(length.bitLength(), Random.asJavaRandom())
        } while (offset >= length)

        respond("Random number from $min to $max: ${min + offset}")
    }
}
