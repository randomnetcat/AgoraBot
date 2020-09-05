package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.digest.DefaultDigestFormat
import org.randomcat.agorabot.digest.JsonGuildDigestMap
import org.randomcat.agorabot.digest.digestEmoteListener
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:

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
}
