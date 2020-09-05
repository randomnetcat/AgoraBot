package org.randomcat.agorabot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.randomcat.agorabot.digest.DigestFormat
import org.randomcat.agorabot.digest.DigestSendStrategy
import org.randomcat.agorabot.digest.SsmtpDigestSendStrategy
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("AgoraBotConfig")

private fun readDigestSendStrategyJson(mailConfig: JsonObject, digestFormat: DigestFormat): DigestSendStrategy? {
    val digestObject = mailConfig["digest"]
    if (digestObject == null) {
        logger.warn(
            "Mail config does not contain digest section! Digest sending will be disabled. " +
                    "Set digest.strategy = \"none\" to silence this warning."
        )
        return null
    }

    if (digestObject !is JsonObject) {
        logger.error("Mail config digest section should be a JSON object!")
        return null
    }

    val sendStrategyName = digestObject["send_strategy"]?.jsonPrimitive?.content
    if (sendStrategyName == null) {
        logger.error("Digest object did not contain send_strategy!")
        return null
    }

    when (sendStrategyName) {
        "none" -> return null

        "ssmtp" -> {
            val ssmtpPath = digestObject["ssmtp_path"]?.jsonPrimitive?.content?.let { Path.of(it) }
            if (ssmtpPath == null) {
                logger.error("ssmtp_path should be set!")
                return null
            }

            val ssmtpConfigPath = digestObject["ssmtp_config_path"]?.jsonPrimitive?.content?.let { Path.of(it) }
            if (ssmtpConfigPath == null) {
                logger.error("ssmtp_config_path should be set!")
                return null
            }

            return SsmtpDigestSendStrategy(
                digestFormat = digestFormat,
                executablePath = ssmtpPath,
                configPath = ssmtpConfigPath,
            )
        }

        else -> {
            logger.error("Unrecognized send_strategy \"$sendStrategyName\"!")
            return null
        }
    }
}

fun readDigestSendStrategyConfig(mailConfigPath: Path, digestFormat: DigestFormat): DigestSendStrategy? {
    require(Files.exists(mailConfigPath)) { "mail.json does not exist" }

    val mailConfig = Json.parseToJsonElement(Files.readString(mailConfigPath, Charsets.UTF_8)).jsonObject

    return readDigestSendStrategyJson(
        mailConfig = mailConfig,
        digestFormat = digestFormat
    )
}
