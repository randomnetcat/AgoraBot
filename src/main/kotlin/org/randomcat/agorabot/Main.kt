package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.commands.RngCommand
import org.randomcat.agorabot.digest.*
import java.nio.file.Path

private fun digestCommand(digestMap: GuildDigestMap): Command {
    val digestFormat = DefaultDigestFormat()

    return DigestCommand(
        digestMap,
        SsmtpDigestSendStrategy(
            digestFormat = digestFormat,
            configPath = Path.of(".", "ssmtp.conf"),
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
                MapCommandRegistry(
                    mapOf(
                        "rng" to RngCommand(),
                        "digest" to digestCommand(digestMap)
                    )
                )
            ),
            BotEmoteListener(digestEmoteListener(digestMap, DISCORD_STAR)),
        )
        .build()
}
