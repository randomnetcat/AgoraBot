package org.randomcat.agorabot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.digest.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private fun readDigestSendStrategy(mailConfig: JsonObject, digestFormat: DigestFormat): DigestSendStrategy? {
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
            val ssmtpPath = digestObject["ssmtp_path"]?.jsonPrimitive?.content.let { Path.of(it) }
            if (ssmtpPath == null) {
                logger.error("ssmtp_path should be set!")
                return null
            }

            val ssmtpConfigPath = digestObject["ssmtp_config_path"]?.jsonPrimitive?.content.let { Path.of(it) }
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

private fun digestCommand(digestMap: GuildDigestMap): Command {
    val mailConfigPath = Path.of(".", "mail.json")
    require(Files.exists(mailConfigPath)) { "mail.json does not exist" }

    val mailConfig = Json.parseToJsonElement(Files.readString(mailConfigPath, Charsets.UTF_8)).jsonObject

    val digestFormat = DefaultDigestFormat()

    return DigestCommand(
        digestMap,
        readDigestSendStrategy(mailConfig = mailConfig, digestFormat = digestFormat),
        digestFormat,
    )
}

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"))

    val prefixMap = JsonPrefixMap(default = ".", Path.of(".", "prefixes"))

    val commandRegistry = MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(),
            "digest" to digestCommand(digestMap),
            "copyright" to CopyrightCommand(),
            "prefix" to PrefixCommand(prefixMap),
        ),
    )

    JDABuilder
        .createDefault(
            token,
            listOf(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
            ),
        )
        .setEventManager(AnnotatedEventManager())
        .addEventListeners(
            BotListener(
                MentionPrefixCommandParser(GuildPrefixCommandParser(prefixMap)),
                commandRegistry,
            ),
            digestEmoteListener(digestMap, DIGEST_ADD_EMOTE),
        )
        .build()

    commandRegistry.addCommand("help", HelpCommand(commandRegistry))
}
