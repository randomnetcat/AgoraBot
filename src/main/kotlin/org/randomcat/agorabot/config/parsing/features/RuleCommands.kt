package org.randomcat.agorabot.config.parsing.features

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.readConfigFromFile
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@Serializable
private data class RuleCommandsConfigDto(
    @SerialName("rule_index_url") val ruleIndexUrlString: String,
) {
    fun toConfig(): RuleCommandsConfig {
        return RuleCommandsConfig(
            ruleIndexUri = URI(ruleIndexUrlString)
        )
    }
}

data class RuleCommandsConfig(
    val ruleIndexUri: URI,
)

fun readRuleCommandsConfig(path: Path): RuleCommandsConfig? {
    return readConfigFromFile(path, default = null) {
        Json.decodeFromString<RuleCommandsConfigDto>(Files.readString(path)).toConfig()
    }
}
