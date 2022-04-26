package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextLong

private fun BigInteger.toLongOrNull(): Long? {
    return if (this > Long.MAX_VALUE.toBigInteger() || this < Long.MIN_VALUE.toBigInteger())
        null
    else
        longValueExact()
}

class RngCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            args(IntArg("max")) { (bigMax) ->
                val max = bigMax.toLongOrNull() ?: run {
                    respond("Provided maximum is invalid.")
                    return@args
                }

                doResponse(min = 1, max = max)
            }

            args(IntArg("min"), IntArg("max")) { (bigMin, bigMax) ->
                val min = bigMin.toLongOrNull() ?: run {
                    respond("Provided minimum is invalid.")
                    return@args
                }

                val max = bigMax.toLongOrNull() ?: run {
                    respond("Provided maximum is invalid.")
                    return@args
                }

                doResponse(min = min, max = max)
            }
        }
    }

    private suspend fun BaseCommandExecutionReceiver.doResponse(min: Long, max: Long) {
        if (min > max) {
            respond("Max value must be at least as big as min value.")
            return
        }

        val result = Random.nextLong(min..max)

        respond("Random number from $min to $max: $result")
    }
}
