@file:OptIn(ExperimentalTime::class)

package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.persistentListOf
import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.listener.CommandRegistry
import org.randomcat.agorabot.util.DiscordMessage
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("AgoraBotIRC")

sealed class RelayConnectedEndpoint {
    abstract fun sendTextMessage(sender: String, content: String)
    abstract fun sendSlashMeTextMessage(sender: String, action: String)
    abstract fun sendDiscordMessage(message: DiscordMessage)

    abstract fun registerSourceEventHandler(
        context: RelayEventHandlerContext,
        otherEndpoints: List<RelayConnectedEndpoint>,
    )

    /**
     * Returns a [CommandOutputSink] if the endpoint is available and can be sent to, or null otherwise.
     */
    abstract fun commandOutputSink(): CommandOutputSink?
}

data class RelayConnectionContext(
    val ircClientMap: IrcClientMap,
    val jda: JDA,
)

data class RelayEventHandlerContext(val commandRegistry: CommandRegistry)

fun initializeIrcRelay(
    ircRelayConfig: IrcRelayConfig,
    connectionContext: RelayConnectionContext,
    commandRegistry: CommandRegistry,
) {
    val ircConnections = ircRelayConfig.entries

    for (ircConnection in ircConnections) {
        logger.info(
            "Connecting IRC channel ${ircConnection.ircChannelName} " +
                    "to Discord channel ${ircConnection.discordChannelId}."
        )

        val endpoints = persistentListOf(
            RelayConnectedDiscordEndpoint(
                jda = connectionContext.jda,
                channelId = ircConnection.discordChannelId,
            ),
            RelayConnectedIrcEndpoint(
                client = connectionContext.ircClientMap.getByName(ircConnection.ircServerName),
                channelName = ircConnection.ircChannelName,
                config = IrcRelayEndpointConfig(
                    commandPrefix = ircConnection.ircCommandPrefix,
                )
            )
        )

        val eventHandlerContext = RelayEventHandlerContext(
            commandRegistry = commandRegistry,
        )

        endpoints.forEachIndexed { index, endpoint ->
            endpoint.registerSourceEventHandler(
                context = eventHandlerContext,
                otherEndpoints = endpoints.removeAt(index),
            )
        }
    }
}
