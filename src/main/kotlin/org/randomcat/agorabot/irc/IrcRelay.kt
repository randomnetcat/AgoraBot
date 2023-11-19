@file:OptIn(ExperimentalTime::class)

package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.guild_state.UserStateMap
import org.randomcat.agorabot.listener.CommandRegistry
import org.randomcat.agorabot.util.DiscordMessage
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("AgoraBotIRC")

data class RelayEventHandlerContext(val commandRegistry: CommandRegistry)

sealed class RelayConnectedEndpoint {
    abstract suspend fun sendTextMessage(sender: String, content: String)
    abstract suspend fun sendSlashMeTextMessage(sender: String, action: String)
    abstract suspend fun sendDiscordMessage(message: DiscordMessage)

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
    val coroutineScope: CoroutineScope,
    val userStateMap: UserStateMap,
)

data class RelayConnectedEndpointMap(
    private val endpointsByName: ImmutableMap<RelayEndpointName, RelayConnectedEndpoint>,
) {
    constructor(
        endpointsByName: Map<RelayEndpointName, RelayConnectedEndpoint>,
    ) : this(endpointsByName.toImmutableMap())

    fun getByName(name: RelayEndpointName): RelayConnectedEndpoint {
        return endpointsByName.getValue(name)
    }
}

fun connectToRelayEndpoints(
    endpointsConfig: RelayEndpointListConfig,
    context: RelayConnectionContext,
): RelayConnectedEndpointMap {
    return RelayConnectedEndpointMap(
        endpointsConfig.endpointsByName.mapValues { (_, config) ->
            when (config) {
                is RelayEndpointConfig.Discord -> {
                    RelayConnectedDiscordEndpoint(
                        jda = context.jda,
                        coroutineScope = context.coroutineScope,
                        userStateMap = context.userStateMap,
                        channelId = config.channelId,
                    )
                }

                is RelayEndpointConfig.Irc -> {
                    val client = context.ircClientMap.getByName(config.serverName)

                    logger.info("Joining channel ${config.channelName} on IRC server ${config.serverName.raw}")
                    client.addChannel(config.channelName)

                    RelayConnectedIrcEndpoint(
                        coroutineScope = context.coroutineScope,
                        client = client,
                        channelName = config.channelName,
                        config = IrcRelayEndpointConfig(
                            commandPrefix = config.commandPrefix,
                        ),
                    )
                }
            }
        }
    )
}

fun initializeIrcRelay(
    config: IrcRelayEntriesConfig,
    connectedEndpointMap: RelayConnectedEndpointMap,
    commandRegistry: CommandRegistry,
) {
    for (entry in config.entries) {
        val endpointNames = entry.endpointNames

        logger.info("Creating bridge between endpoints: ${endpointNames.map { it.raw }}")

        val endpoints = endpointNames.map { connectedEndpointMap.getByName(it) }.toPersistentList()

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

fun formatRelayDiscordContent(content: String): String {
    // Remove backslash used to indicate that message should always be forwarded
    return content.removePrefix("\\")
}
