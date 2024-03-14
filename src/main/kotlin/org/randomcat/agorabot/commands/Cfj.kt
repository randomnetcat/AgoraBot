package org.randomcat.agorabot.commands

import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.help.help
import org.randomcat.agorabot.util.repeated
import org.randomcat.agorabot.util.userFacingRandom
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
    "RUDE AND GROSS",
)

private val MODERATE_RESPONSES = listOf(
    "DISMISS",
    "IRRELEVANT",
)

private val LIKELY_RESPONSES = listOf(
    "TRUE",
    "FALSE",
)

private val RESPONSES = LIKELY_RESPONSES.repeated(10) + MODERATE_RESPONSES.repeated(2) + UNLIKELY_RESPONSES

private fun randomShouldFourFactor(): Boolean {
    return Random.nextInt(FOUR_FACTORS_LOTS) == 0
}

private fun randomResponse(): String {
    return RESPONSES.random(userFacingRandom())
}

private suspend fun BaseCommandExecutionReceiver.respondFourFactor() {
    fun textForFactor(factor: String): String {
        val isTrue = userFacingRandom().nextBoolean()
        return "The statement is supported by $factor: ${if (isTrue) "TRUE" else "FALSE"}."
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

private fun formatResponse(statement: String, response: String): String {
    return "\"$statement\" judged $response."
}

class CfjCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    override fun BaseCommandImplReceiver.impl() {
        help("Returns random Agoran-style CFJ judgements.")

        matchFirst {
            noArgs().help("Returns a judgement without an associated statement") { _ ->
                if (randomShouldFourFactor()) {
                    respondFourFactor()
                }

                respond("Judged ${randomResponse()}.")
            }

            args(RemainingStringArgs("statement")).help("Returns a judgement on the specified statement") { (statementParts) ->
                if (randomShouldFourFactor()) {
                    respondFourFactor()
                }

                val statement = statementParts.joinToString(" ")
                val response = randomResponse()

                respond(
                    formatResponse(
                        statement = statement,
                        response = response,
                    ),
                )
            }
        }
    }
}
