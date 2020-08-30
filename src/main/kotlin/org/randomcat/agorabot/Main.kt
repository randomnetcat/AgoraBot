package org.randomcat.agorabot

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.commands.HelpCommand
import org.randomcat.agorabot.commands.RngCommand
import org.randomcat.agorabot.digest.*
import java.nio.file.Files
import java.nio.file.Path

private fun digestCommand(digestMap: GuildDigestMap): Command {
    val mailConfigPath = Path.of(".", "mail.json")
    require(Files.exists(mailConfigPath)) { "mail.json does not exist" }

    val mailConfig = Json.parseToJsonElement(Files.readString(mailConfigPath, Charsets.UTF_8)).jsonObject

    val ssmtpPath = Path.of(
        (mailConfig["ssmtp_path"] ?: error("must specify ssmtp path in mail config"))
            .jsonPrimitive
            .content
    )

    val ssmtpConfigPath = Path.of(
        (mailConfig["ssmtp_config_path"] ?: error("must specify ssmtp config path in mail config"))
            .jsonPrimitive
            .content
    )

    val digestFormat = DefaultDigestFormat()

    return DigestCommand(
        digestMap,
        SsmtpDigestSendStrategy(
            digestFormat = digestFormat,
            executablePath = ssmtpPath,
            configPath = ssmtpConfigPath,
        ),
        digestFormat,
    )
}

private const val DISCORD_WHITE_CHECK_MARK = "\u2705"

private fun digestEmoteListener(digestMap: GuildDigestMap, targetEmoji: String): (MessageReactionAddEvent) -> Unit {
    val functor = object {
        operator fun invoke(event: MessageReactionAddEvent) {
            val emote = event.reactionEmote
            if (!emote.isEmoji) return

            val reactionEmoji = emote.emoji
            if (reactionEmoji == targetEmoji) {
                val digest = digestMap.digestForGuild(event.guild.id)

                event.retrieveMessage().queue { message ->
                    val numAdded = digest.addCounted(message.toDigestMessage())

                    if (numAdded > 0) {
                        message
                            .addReaction(DISCORD_WHITE_CHECK_MARK)
                            .mapToResult() // Ignores failure if no permission to react
                            .queue()
                    }
                }
            }
        }
    }

    return { functor(it) }
}

private const val DISCORD_STAR = "\u2B50"

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()
    val digestMap = JsonGuildDigestMap(Path.of(".", "digests"))

    val commandRegistry = MutableMapCommandRegistry(
        mapOf(
            "rng" to RngCommand(),
            "digest" to digestCommand(digestMap)
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
                GlobalPrefixCommandParser("."),
                commandRegistry,
            ),
            BotEmoteListener(digestEmoteListener(digestMap, DISCORD_STAR)),
        )
        .build()

    commandRegistry.addCommand("help", HelpCommand(commandRegistry))
}
