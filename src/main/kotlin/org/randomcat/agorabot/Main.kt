package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.digest.DefaultDigestFormat
import org.randomcat.agorabot.digest.JsonGuildDigestMap
import org.randomcat.agorabot.digest.digestEmoteListener
import org.slf4j.LoggerFactory
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

private val logger = LoggerFactory.getLogger("AgoraBot")

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"))

    val prefixMap = JsonPrefixMap(default = ".", Path.of(".", "prefixes"))
    val digestFormat = DefaultDigestFormat()

    val commandRegistry = MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(),
            "digest" to DigestCommand(
                digestMap = digestMap,
                sendStrategy = readDigestSendStrategyConfig(Path.of(".", "mail.json"), digestFormat),
                digestFormat = digestFormat,
            ),
            "copyright" to CopyrightCommand(),
            "prefix" to PrefixCommand(prefixMap),
        ),
    )

    commandRegistry.addCommand("help", HelpCommand(commandRegistry))

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
                    commandRegistry,
                ),
                digestEmoteListener(digestMap, DIGEST_ADD_EMOTE),
            )
            .build()

    while (jda.status != JDA.Status.CONNECTED) {
        // Spin. The remainder of the setup depends on JDA functioning.
        logger.info("Waiting for JDA, current state: ${jda.status}")
        Thread.sleep(1000)
    }

    val ircDir = Path.of(".", "irc")
    val ircConfig = readIrcConfig(ircDir.resolve("config.json"))

    if (ircConfig == null) {
        logger.warn("Unable to setup IRC! Check for errors above.")
    } else {
        val ircClient = setupIrcClient(
            config = ircConfig.global,
            ircDir = ircDir,
        )

        val ircConnections = ircConfig.connections

        for (ircConnection in ircConnections) {
            logger.info(
                "Connecting IRC channel ${ircConnection.ircChannelName} " +
                        "to Discord channel ${ircConnection.discordChannelId}."
            )

            connectIrcAndDiscordChannels(ircClient, jda, ircConnection)
        }
    }
}
