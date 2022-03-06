package org.randomcat.agorabot.config

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.randomcat.agorabot.digest.DigestFormat
import org.randomcat.agorabot.digest.DigestSendStrategy
import org.randomcat.agorabot.digest.SsmtpDigestSendStrategy
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("AgoraBotConfig")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("send_strategy")
private sealed class DigestSendStrategyDto {
    @Serializable
    @SerialName("none")
    object None : DigestSendStrategyDto()

    @Serializable
    @SerialName("ssmtp")
    data class Ssmtp(
        @SerialName("ssmtp_path")
        val ssmtpPathString: String,
        @SerialName("ssmtp_config_path")
        val configPathString: String,
    ) : DigestSendStrategyDto()
}

private fun readSendStrategyDigestObjectJson(
    resolveUsedPath: (String) -> File,
    configText: String,
    digestFormat: DigestFormat,
    botStorageDir: Path,
): DigestSendStrategy? {
    return when (val config = Json.decodeFromString<DigestSendStrategyDto>(configText)) {
        is DigestSendStrategyDto.None -> null

        is DigestSendStrategyDto.Ssmtp -> {
            return SsmtpDigestSendStrategy(
                digestFormat = digestFormat,
                executablePath = resolveUsedPath(config.ssmtpPathString),
                configPath = resolveUsedPath(config.configPathString),
                storageDir = botStorageDir.resolve("digest_send").resolve("ssmtp").toFile(),
            )
        }
    }
}

fun readDigestMailConfig(
    digestMailConfigPath: Path,
    digestFormat: DigestFormat,
    botStorageDir: Path,
): DigestSendStrategy? {
    if (Files.notExists(digestMailConfigPath)) {
        logger.warn("Unable to open digest mail config path \"$digestMailConfigPath\"!")
        return null
    }

    val configPathAsFile = digestMailConfigPath.toAbsolutePath().toFile()

    return try {
        readSendStrategyDigestObjectJson(
            resolveUsedPath = { configPathAsFile.resolveSibling(it) },
            configText = Files.readString(digestMailConfigPath, Charsets.UTF_8),
            digestFormat = digestFormat,
            botStorageDir = botStorageDir,
        )
    } catch (e: Exception) {
        logger.error("Unable to parse digest mail config", e)
        null
    }
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
