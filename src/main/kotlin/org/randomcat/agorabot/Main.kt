package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.digest.*
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

private fun makeCommandRegistry(
    prefixMap: MutableGuildPrefixMap,
    digestMap: GuildDigestMap,
    digestFormat: DigestFormat,
    digestSendStrategy: DigestSendStrategy?,
): CommandRegistry {
    return MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(),
            "digest" to DigestCommand(
                digestMap = digestMap,
                sendStrategy = digestSendStrategy,
                digestFormat = digestFormat,
            ),
            "copyright" to CopyrightCommand(),
            "prefix" to PrefixCommand(prefixMap),
        ),
    ).also { it.addCommand("help", HelpCommand(it)) }
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val persistService: ConfigPersistService = DefaultConfigPersistService

    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"), persistService)

    val prefixMap = JsonPrefixMap(default = ".", Path.of(".", "prefixes"), persistService)
    val digestFormat = DefaultDigestFormat()

    val digestSendStrategy = readDigestSendStrategyConfig(Path.of(".", "mail.json"), digestFormat)
    if (digestSendStrategy == null) {
        logger.warn("Unable to setup digest sending! Check for errors above.")
    }

    val jda =
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
                    makeCommandRegistry(prefixMap, digestMap, digestFormat, digestSendStrategy),
                ),
                digestEmoteListener(digestMap, DIGEST_ADD_EMOTE),
            )
            .build()

    jda.awaitReady()

    val ircDir = Path.of(".", "irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    if (ircConfig == null) {
        logger.warn("Unable to setup IRC! Check for errors above.")
    } else {
        setupIrc(ircConfig, ircDir, jda)
    }
}
