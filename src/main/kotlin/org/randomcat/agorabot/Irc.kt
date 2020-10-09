@file:OptIn(ExperimentalTime::class)

package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.Client.Builder.Server.SecurityType
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.feature.sts.StsPropertiesStorageManager
import org.randomcat.agorabot.util.disallowMentions
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.minutes

private val logger = LoggerFactory.getLogger("AgoraBotIRC")

/**
 * In order to provide more information in the name than just "Client".
 */
typealias IrcClient = Client

fun setupIrcClient(config: IrcGlobalConfig, ircDir: Path): IrcClient {
    return Client
        .builder()
        .nick(config.nickname)
        .server()
        .host(config.server)
        .port(config.port, if (config.serverIsSecure) SecurityType.SECURE else SecurityType.INSECURE)
        .then()
        .management()
        .stsStorageManager(StsPropertiesStorageManager(ircDir.resolve("kicl_sts_storage")))
        .then()
        .buildAndConnect()
}

private const val MAX_IRC_LENGTH = 500

typealias IrcChannel = Channel
typealias DiscordMessage = Message

fun IrcChannel.sendDiscordMessage(message: DiscordMessage) {
    val senderName = message.member?.nickname ?: message.author.name

    val fullMessage =
        senderName +
                " says: " +
                message.contentDisplay +
                (message.attachments
                    .map { it.url }
                    .filter { it.length <= MAX_IRC_LENGTH }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n", prefix = "\n")
                    ?: "")

    fullMessage.lines().forEach { sendMultiLineMessage(it) }
}

private fun connectIrcAndDiscordChannels(ircClient: IrcClient, jda: JDA, connection: IrcConnectionConfig) {
    val discordChannelId = connection.discordChannelId
    val ircChannelName = connection.ircChannelName

    // Don't hold on to the channel because JDA doesn't allow keeping objects for indefinite time periods.
    if (jda.getTextChannelById(connection.discordChannelId) == null) {
        logger.error("Could not find Discord channel ${connection.discordChannelId} " +
                "when connecting to IRC channel ${connection.ircChannelName}!")
        return
    }

    ircClient.addChannel(connection.ircChannelName)
    ircClient.eventManager.registerEventListener(IrcListener(object : IrcMessageHandler {
        private var isDisarmed = false

        override fun onMessage(event: ChannelMessageEvent) {
            if (isDisarmed) return
            if (event.channel.name != ircChannelName) return
            if (event.actor.nick == event.client.nick) return

            val discordChannel = jda.getTextChannelById(discordChannelId)

            if (discordChannel == null) {
                logger.error(
                    "Discord channel $discordChannelId could not be found in order to send a message! " +
                            "Disarming this connection."
                )

                return
            }

            discordChannel
                .sendMessage(event.actor.nick + " says: " + event.message)
                .disallowMentions()
                .queue()
        }
    }))

    val startTime = TimeSource.Monotonic.markNow()

    // The IRC client takes some time to connect, so we won't disarm the IRC side until one minute has passed since it
    // *should* have connected. 1 minute should (hopefully) be plenty of time.
    val ircGraceEnd = startTime + 1.minutes

    jda.addEventListener(object {
        private var isDisarmed = false

        @SubscribeEvent
        fun onMessage(event: GuildMessageReceivedEvent) {
            if (isDisarmed) return
            if (event.channel.id != discordChannelId) return
            if (event.author.id == event.jda.selfUser.id) return

            val optChannel = ircClient.getChannel(ircChannelName)

            optChannel.ifPresentOrElse({ channel ->
                channel.sendDiscordMessage(event.message)
            }, {
                if (ircGraceEnd.hasPassedNow()) {
                    logger.error(
                        "IRC channel $ircChannelName could not be found in order to send a message! " +
                                "Disarming this connection."
                    )
                } else {
                    logger.warn(
                        "IRC channel $ircChannelName could not be found in order to send a message! " +
                                "Not disarming because of grace period."
                    )
                }
            })
        }
    })
}

fun setupIrc(
    ircConfig: IrcConfig,
    ircDir: Path,
    jda: JDA,
): IrcClient {
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

    return ircClient
}
