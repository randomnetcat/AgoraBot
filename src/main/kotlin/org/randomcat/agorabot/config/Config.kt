package org.randomcat.agorabot.config

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*
import org.randomcat.agorabot.digest.DigestFormat
import org.randomcat.agorabot.digest.DigestSendStrategy
import org.randomcat.agorabot.digest.SsmtpDigestSendStrategy
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("AgoraBotConfig")

private fun readSendStrategyDigestObjectJson(
    digestObject: JsonObject,
    digestFormat: DigestFormat
): DigestSendStrategy? {
    val sendStrategyName = (digestObject["send_strategy"] as? JsonPrimitive)?.content
    if (sendStrategyName == null) {
        logger.error("Digest object should contain send_strategy and be a JSON primitive!")
        return null
    }

    when (sendStrategyName) {
        "none" -> return null

        "ssmtp" -> {
            val ssmtpPath = (digestObject["ssmtp_path"] as? JsonPrimitive)?.content?.let { Path.of(it) }
            if (ssmtpPath == null) {
                logger.error("For ssmtp, send_strategy.ssmtp_path should exist and be a JSON primitive!")
                return null
            }

            val ssmtpConfigPath = (digestObject["ssmtp_config_path"] as? JsonPrimitive)?.content?.let { Path.of(it) }
            if (ssmtpConfigPath == null) {
                logger.error("For ssmtp, send_strategy.ssmtp_config_path should exist and be a JSON primitive!")
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

    return readSendStrategyDigestObjectJson(digestObject, digestFormat)
}

fun readGlobalMailDotJsonConfig(mailConfigPath: Path, digestFormat: DigestFormat): DigestSendStrategy? {
    if (Files.notExists(mailConfigPath)) {
        logger.warn("Unable to find mail.json!")
        return null
    }

    val mailConfig = Json.parseToJsonElement(Files.readString(mailConfigPath, Charsets.UTF_8)).jsonObject

    return readDigestSendStrategyJson(
        mailConfig = mailConfig,
        digestFormat = digestFormat
    )
}

fun readDigestMailConfig(digestMailConfigPath: Path, digestFormat: DigestFormat): DigestSendStrategy? {
    if (Files.notExists(digestMailConfigPath)) {
        logger.warn("Unable to open digest mail config path \"$digestMailConfigPath\"!")
        return null
    }

    val digestMailConfig = Json.parseToJsonElement(Files.readString(digestMailConfigPath, Charsets.UTF_8)).jsonObject

    return readSendStrategyDigestObjectJson(
        digestObject = digestMailConfig,
        digestFormat = digestFormat,
    )
}

data class PermissionsConfig(
    val botAdmins: ImmutableList<String>,
) {
    constructor(botAdminList: List<String>) : this(botAdminList.toImmutableList())
}

fun readPermissionsConfig(configPath: Path): PermissionsConfig? {
    if (Files.notExists(configPath)) {
        logger.warn("Permissions config path $configPath does not exist!")
        return null
    }

    val json = Json.parseToJsonElement(Files.readString(configPath, Charsets.UTF_8))
    if (json !is JsonObject) {
        logger.error("Permissions config should be a JSON object!")
        return null
    }

    val adminList = json["admins"] ?: return PermissionsConfig(botAdminList = emptyList())

    if (adminList !is JsonArray || adminList.any { it !is JsonPrimitive }) {
        logger.error("permissions.admins should be an array of user IDs!")
        return null
    }

    return PermissionsConfig(
        botAdminList = adminList.map { (it as JsonPrimitive).content }
    )
}
