package org.randomcat.agorabot.config.parsing.features

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.readConfigFromFile
import java.nio.file.Path

@Serializable
data class SecretHitlerFeatureConfig(
    @SerialName("enable_impersonation") val enableImpersonation: Boolean = false,
)

fun readSecretHitlerConfig(configPath: Path): SecretHitlerFeatureConfig? {
    return readConfigFromFile(path = configPath, default = null) { text ->
        Json.decodeFromString<SecretHitlerFeatureConfig>(text)
    }
}
