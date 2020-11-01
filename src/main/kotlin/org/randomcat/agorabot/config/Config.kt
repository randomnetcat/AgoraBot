package org.randomcat.agorabot.config

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.*
import org.randomcat.agorabot.digest.DigestFormat
import org.randomcat.agorabot.digest.DigestSendStrategy
import org.randomcat.agorabot.digest.SsmtpDigestSendStrategy
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.IrcConnectionConfig
import org.randomcat.agorabot.irc.IrcServerConfig
import org.randomcat.agorabot.irc.IrcUserConfig
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

fun readDigestSendStrategyConfig(mailConfigPath: Path, digestFormat: DigestFormat): DigestSendStrategy? {
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

private fun JsonObject.readIrcJsonString(name: String): String? {
    val value = (this[name] as? JsonPrimitive?)?.takeIf { it.isString }?.content
    if (value == null) {
        logger.error("IRC $name should exist and be a string!")
        return null
    }

    return value
}

private fun JsonObject.readIrcJsonInt(name: String): Int? {
    val value = (this[name] as? JsonPrimitive?)?.intOrNull
    if (value == null) {
        logger.error("IRC $name should exist and be an int!")
    }

    return value
}

private fun JsonObject.readIrcJsonBoolean(name: String): Boolean? {
    val value = (this[name] as? JsonPrimitive?)?.booleanOrNull
    if (value == null) {
        logger.error("IRC $name should exist and be a bool!")
    }

    return value
}

private fun JsonObject.readIrcConnections(): List<IrcConnectionConfig>? {
    val jsonConnections = (this["connections"] as? JsonArray)
    if (jsonConnections == null) {
        logger.error("IRC connections should exist and be an array!")
        return null
    }

    return jsonConnections.mapNotNull {
        if (it !is JsonObject) {
            logger.warn("IRC connection entries should be objects!")
            return@mapNotNull null
        }

        val ircChannel = (it["irc_channel"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (ircChannel == null) {
            logger.warn("IRC connection irc_channel should exist and be a string!")
            return@mapNotNull null
        }

        val discordChannel = (it["discord_channel_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (discordChannel == null) {
            logger.warn("IRC connection discord_channel_id should exist and be a string!")
            return@mapNotNull null
        }

        val relayJoinLeave = (it["relay_join_leave"] as? JsonPrimitive)?.booleanOrNull ?: false

        IrcConnectionConfig(
            ircChannelName = ircChannel,
            discordChannelId = discordChannel,
            relayJoinLeaveMessages = relayJoinLeave,
        )
    }
}

private fun readIrcConfigJson(jsonObject: JsonObject): IrcConfig? {
    val nickname = jsonObject.readIrcJsonString("nickname") ?: return null
    val server = jsonObject.readIrcJsonString("server") ?: return null
    val port = jsonObject.readIrcJsonInt("port") ?: return null
    val serverIsSecure = jsonObject.readIrcJsonBoolean("server_is_secure") ?: return null

    return IrcConfig(
        IrcServerConfig(
            server = server,
            port = port,
            serverIsSecure = serverIsSecure,
        ),
        IrcUserConfig(
            nickname = nickname,
        ),
        jsonObject.readIrcConnections()?.toImmutableList() ?: return null,
    )
}

fun readIrcConfig(configPath: Path): IrcConfig? {
    if (Files.notExists(configPath)) {
        logger.warn("IRC config path $configPath does not exist!")
        return null
    }

    val json = Json.parseToJsonElement(Files.readString(configPath, Charsets.UTF_8))
    if (json !is JsonObject) {
        logger.error("IRC config should be a JSON object!")
        return null
    }

    return readIrcConfigJson(json)
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
