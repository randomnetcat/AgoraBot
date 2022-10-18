package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.BaseCommand
import org.randomcat.agorabot.commands.base.BaseCommandImplReceiver
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.commands.base.RemainingStringArgs
import org.randomcat.agorabot.util.DISCORD_MAX_MESSAGE_LENGTH
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.asJavaRandom

private sealed class DieSpecification {
    data class Constant(val value: BigInteger) : DieSpecification() {
        override fun roll(random: Random): BigInteger {
            return value
        }
    }

    data class NormalDie(val maxValue: BigInteger) : DieSpecification() {
        init {
            require(maxValue >= BigInteger.ONE)
        }

        override fun roll(random: Random): BigInteger {
            var candidate: BigInteger

            do {
                candidate = BigInteger(maxValue.bitLength(), random.asJavaRandom()) + BigInteger.ONE
            } while (candidate > maxValue)

            return candidate
        }
    }

    abstract fun roll(random: Random): BigInteger
}

private data class ParsedDie(
    val spec: String,
    val die: DieSpecification,
)

private fun parseSingleDieSpec(spec: String): ParsedDie {
    return ParsedDie(
        spec,
        when (spec.first()) {
            '+', '-' -> DieSpecification.Constant(spec.toBigInteger())

            'd' -> DieSpecification.NormalDie(spec.removePrefix("d").toBigInteger())

            else -> throw IllegalArgumentException("Invalid die spec: $spec")
        }
    )
}

private fun parseMultiDiceSpec(spec: String): Sequence<ParsedDie> {
    require(spec.isNotEmpty())

    val count = if (spec.first() in '0'..'9') spec.takeWhile { it in '0'..'9' }.toInt() else 1
    val singleSpec = parseSingleDieSpec(spec.dropWhile { it in '0'..'9' })

    return (0 until count).asSequence().map { singleSpec }
}

private val DICE_REGEX = Regex("(?:\\d+)?(?:(?:d[1-9][0-9]*)|(?:[+-]\\d+))")

private fun String.isValidDiceSpec() = matches(DICE_REGEX)

class RollCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        args(RemainingStringArgs("dice_specs")) { (diceSpecs) ->
            if (diceSpecs.isEmpty()) {
                respond("What do you want me to roll?")
                return@args
            }

            if (diceSpecs.singleOrNull() == "newspaper") {
                respond("\uD83D\uDDDE️")
                return@args
            }

            run {
                val firstInvalid = diceSpecs.firstOrNull { !it.isValidDiceSpec() }

                if (firstInvalid != null) {
                    respond("Invalid dice specification: $firstInvalid")
                    return@args
                }
            }

            // This cannot be a Sequence because the map call is non-deterministic and the rolls are iterated
            // multiple times.
            val rolls = diceSpecs
                .flatMap { stringSpec ->
                    parseMultiDiceSpec(stringSpec)
                }
                .map { parsedDie ->
                    parsedDie.die.roll(Random)
                }

            val rollStr = rolls.joinToString(
                separator = ", ",
                prefix = "[", postfix = "]",
            ) { roll ->
                roll.toString()
            }

            val sumStr = "Sum: ${rolls.sumOf { it }}"

            val fullStr = "$rollStr. $sumStr"

            if (fullStr.length < DISCORD_MAX_MESSAGE_LENGTH)
                respond(fullStr)
            else
                respondWithTextAndFile(text = sumStr, fileName = "rng.txt", fileContent = fullStr)
        }
    }
}
