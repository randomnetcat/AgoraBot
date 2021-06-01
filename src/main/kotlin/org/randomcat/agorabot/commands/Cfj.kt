package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.util.repeated
import kotlin.random.Random

private const val FOUR_FACTORS_LOTS = 10

private val UNLIKELY_RESPONSES = listOf(
    "SHENANIGANS",
    "I GUESS",
    "PROBABLY",
    "WHO KNOWS",
    "OH GOD, DEFINITELY NOT",
    "DON'T ASK ME",
    "THE ONLY REAL ARGUMENT IN FAVOR OF THIS IS WISHFUL THINKING",
    "A TYPICAL EXAMPLE OF \"I SAY I DO, THEREFORE I DO\" WHICH HAS PLAGUED AGORA FOR A LONG TIME",
    "IF YOU SAY SO",
    "UNFORTUNATELY",
    "IT'S IN THE BEST INTEREST OF THE GAME FOR THIS TO BE TRUE",
    "PARADOXICAL",
    "HAVE YOU TRIED TURNING IT OFF AND TURNING IT ON AGAIN",
)

private val MODERATE_RESOPONSES = listOf(
    "DISMISS",
    "IRRELEVANT",
)

private val LIKELY_RESPONSES = listOf(
    "TRUE",
    "FALSE",
)

private val RESPONSES = LIKELY_RESPONSES.repeated(10) + MODERATE_RESOPONSES.repeated(2) + UNLIKELY_RESPONSES

private fun randomShouldFourFactor(): Boolean {
    return Random.nextInt(FOUR_FACTORS_LOTS) == 0
}

private fun randomResponse(): String {
    return RESPONSES.random()
}

private fun BaseCommandExecutionReceiver.respondFourFactor() {
    fun textForFactor(factor: String): String {
        val isTrue = Random.nextBoolean()
        return "Is the statement supported by $factor? ${if (isTrue) "TRUE" else "FALSE"}"
    }

    respond("I will now begin a full four factors analysis.")

    val fourFactorsResponse = buildString {
        appendLine(textForFactor("game custom"))
        appendLine(textForFactor("common sense"))
        appendLine(textForFactor("past judgements"))
        appendLine(textForFactor("consideration of the best interests of the game"))
    }

    respond(fourFactorsResponse)
}

class CfjCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        matchFirst {
            noArgs { _ ->
                if (randomShouldFourFactor()) {
                    respondFourFactor()
                }

                respond("Judged ${randomResponse()}.")
            }

            args(RemainingStringArgs("statement")) { (statementParts) ->
                if (randomShouldFourFactor()) {
                    respondFourFactor()
                }

                val statement = statementParts.joinToString(" ")
                respond("\"$statement\" judged ${randomResponse()}.")
            }
        }
    }
}
