package org.randomcat.agorabot.config.parsing.features

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.randomcat.agorabot.config.BadConfigException
import org.randomcat.agorabot.features.CFJ_URL_NUMBER_REPLACEMENT
import org.randomcat.agorabot.features.RULE_URL_NUMBER_REPLACMENT
import java.nio.file.Path
import kotlin.io.path.readText

data class CitationsConfig(
    val ruleUrlPattern: String?,
    val cfjUrlPattern: String?,
)

private fun parseText(text: String): CitationsConfig {
    val json = Json.parseToJsonElement(text)

    if (json !is JsonObject) {
        throw BadConfigException("Citations config should be a JSON object!")
    }

    fun readPattern(key: String, expectedReplacement: String): String? {
        return (json[key] as? JsonPrimitive)?.let { ruleUrlPatternPrimitive ->
            if (ruleUrlPatternPrimitive.isString) {
                ruleUrlPatternPrimitive.content.takeIf { it.contains(expectedReplacement) } ?: run {
                    throw BadConfigException("Citation config $key should contain \"$expectedReplacement\"")
                }
            } else {
                throw BadConfigException("Citation config $key should be a string!")
            }
        }
    }

    val ruleUrlPattern = readPattern(key = "rule_url_format", expectedReplacement = RULE_URL_NUMBER_REPLACMENT)
    val cfjUrlPattern = readPattern(key = "cfj_url_format", expectedReplacement = CFJ_URL_NUMBER_REPLACEMENT)

    return CitationsConfig(
        ruleUrlPattern = ruleUrlPattern,
        cfjUrlPattern = cfjUrlPattern,
    )
}

fun readCitationsConfig(configPath: Path): CitationsConfig {
    return parseText(configPath.readText())
}
