package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.util.DISCORD_MAX_MESSAGE_LENGTH
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextLong

private sealed class DieSpecification {
    data class Constant(val value: BigInteger) : DieSpecification() {
        override fun roll(random: Random): BigInteger {
            return value
        }
    }

    data class NormalDie(val maxValue: Long) : DieSpecification() {
        init {
            require(maxValue >= 1)
        }

        override fun roll(random: Random): BigInteger {
            return random.nextLong(1..maxValue).toBigInteger()
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

            'd' -> DieSpecification.NormalDie(spec.removePrefix("d").toLong())

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
    override fun TopLevelArgumentDescriptionReceiver<ExecutionReceiverImpl, PermissionsExtensionMarker>.impl() {
        args(RemainingStringArgs("dice_specs")) { (diceSpecs) ->
            if (diceSpecs.isEmpty()) {
                respond("What do you want me to roll?")
                return@args
            }

            run {
                val firstInvalid = diceSpecs.firstOrNull { !it.isValidDiceSpec() }

                if (firstInvalid != null) {
                    respond("Invalid dice specification: $firstInvalid")
                    return@args
                }
            }

            diceSpecs
                .asSequence()
                .flatMap { stringSpec ->
                    parseMultiDiceSpec(stringSpec)
                }
                .map { parsedDie ->
                    parsedDie.die.roll(Random)
                }
                .let { rolls ->
                    rolls.joinToString(
                        separator = ", ",
                        prefix = "[", postfix = "]. Sum: ${rolls.sumOf { it }}",
                    ) { roll ->
                        roll.toString()
                    }
                }
                .let {
                    if (it.length < DISCORD_MAX_MESSAGE_LENGTH)
                        respond(it)
                    else
                        respondWithFile(fileName = "rng.txt", fileContent = it)
                }
        }
    }
}
