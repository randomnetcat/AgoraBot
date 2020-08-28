package org.randomcat.agorabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.commands.RngCommand
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 1) { "Single command line argument of token required" }

    val token = args.single()

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
                        "digest" to DigestCommand(
                            JsonDigestMap(Path.of(".", "digests")),
                            SsmtpDigestSendStrategy()
                        )
                    )
                )
            )
        )
        .build()
}
