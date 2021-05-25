@file:OptIn(ExperimentalTime::class)

package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.persistentListOf
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.helper.ActorEvent
import org.kitteh.irc.client.library.event.helper.ChannelEvent
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.disallowMentions
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("AgoraBotIRC")

private fun IrcChannel.sendDiscordMessage(message: DiscordMessage) {
    val senderName = message.member?.nickname ?: message.author.name

    val fullMessage =
        senderName +
                " says: " +
                message.contentDisplay +
                (message.attachments
                    .map { it.url }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n", prefix = "\n")
                    ?: "")

    sendSplitMultiLineMessage(fullMessage)
}

private fun formatIrcNameForDiscord(name: String): String {
    return "**$name**"
}

private sealed class RelayConnectedEndpoint {
    data class Discord(private val jda: JDA, private val channelId: String) : RelayConnectedEndpoint() {
        companion object {
            private fun relayToChannel(channel: MessageChannel, text: String) {
                (MessageBuilder(text).buildAll(MessageBuilder.SplitPolicy.NEWLINE)).forEach {
                    channel
                        .sendMessage(it)
                        .disallowMentions()
                        .queue()
                }
            }
        }

        private inline fun tryWithChannel(block: (MessageChannel) -> Unit) {
            jda.getTextChannelById(channelId)?.let(block)
        }

        override fun sendTextMessage(sender: String, content: String) {
            tryWithChannel { channel ->
                relayToChannel(channel, formatIrcNameForDiscord(sender) + " says: " + content)
            }
        }

        override fun sendSlashMeTextMessage(sender: String, action: String) {
            tryWithChannel { channel ->
                relayToChannel(channel, formatIrcNameForDiscord(sender) + " " + action)
            }
        }

        override fun sendDiscordMessage(message: DiscordMessage) {
            tryWithChannel { channel ->
                channel.sendMessage(message)
            }
        }

        override fun registerSourceEventHandler(
            context: RelayEventHandlerContext,
            otherEndpoints: List<RelayConnectedEndpoint>,
        ) {
            addDiscordRelay(
                jda = jda,
                channelId = channelId,
                endpoints = otherEndpoints,
            )
        }
    }

    data class Irc(
        private val client: IrcClient,
        private val channelName: String,
        private val config: IrcRelaySourceConfig,
    ) : RelayConnectedEndpoint() {
        private inline fun tryWithChannel(block: (IrcChannel) -> Unit) {
            client.getChannel(channelName).orElse(null)?.let(block)
        }

        override fun sendTextMessage(sender: String, content: String) {
            tryWithChannel { channel ->
                channel.sendSplitMultiLineMessage("$sender says: $content")
            }
        }

        override fun sendSlashMeTextMessage(sender: String, action: String) {
            tryWithChannel { channel ->
                channel.sendSplitMultiLineMessage("$sender $action")
            }
        }

        override fun sendDiscordMessage(message: DiscordMessage) {
            tryWithChannel { channel ->
                channel.sendDiscordMessage(message)
            }
        }

        override fun registerSourceEventHandler(
            context: RelayEventHandlerContext,
            otherEndpoints: List<RelayConnectedEndpoint>,
        ) {
            client.addChannel(channelName)

            addIrcRelay(
                ircClient = client,
                ircChannelName = channelName,
                config = config,
                commandRegistry = context.commandRegistry,
                endpoints = otherEndpoints,
            )
        }
    }

    abstract fun sendTextMessage(sender: String, content: String)
    abstract fun sendSlashMeTextMessage(sender: String, action: String)
    abstract fun sendDiscordMessage(message: DiscordMessage)

    abstract fun registerSourceEventHandler(
        context: RelayEventHandlerContext,
        otherEndpoints: List<RelayConnectedEndpoint>,
    )
}

private data class IrcRelaySourceConfig(
    val commandPrefix: String?,
)

private fun ircCommandParser(config: IrcRelaySourceConfig): CommandParser? {
    val prefix = config.commandPrefix ?: return null
    return GlobalPrefixCommandParser(prefix)
}

private fun addIrcRelay(
    ircClient: IrcClient,
    ircChannelName: String,
    config: IrcRelaySourceConfig,
    commandRegistry: CommandRegistry,
    endpoints: List<RelayConnectedEndpoint>,
) {
    ircClient.eventManager.registerEventListener(IrcListener(object : IrcMessageHandler {
        private val commandParser = ircCommandParser(config)

        private fun <E> E.isInRelevantChannel() where E : ActorEvent<IrcUser>, E : ChannelEvent =
            channel.name == ircChannelName

        private inline fun forEachEndpoint(block: (RelayConnectedEndpoint) -> Unit) {
            endpoints.forEach(block)
        }

        override fun onMessage(event: ChannelMessageEvent) {
            if (!event.isInRelevantChannel()) return

            forEachEndpoint {
                it.sendTextMessage(sender = event.actor.nick, content = event.message)
            }

            if (commandParser != null) {
                val source = CommandEventSource.Irc(event)

                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (val parseResult = commandParser.parse(source)) {
                    is CommandParseResult.Ignore -> {
                    }

                    is CommandParseResult.Invocation -> {
                        commandRegistry.invokeCommand(source, parseResult.invocation)
                    }
                }
            }
        }

        override fun onCtcpMessage(event: ChannelCtcpEvent) {
            if (!event.isInRelevantChannel()) return

            val message = event.message
            if (!message.startsWith("ACTION ")) return // ACTION means a /me command

            forEachEndpoint {
                it.sendSlashMeTextMessage(sender = event.actor.nick, action = message.removePrefix("ACTION "))
            }
        }
    }))
}

private fun addDiscordRelay(
    jda: JDA,
    channelId: String,
    endpoints: List<RelayConnectedEndpoint>,
) {
    jda.addEventListener(object {
        private inline fun forEachEndpoint(block: (RelayConnectedEndpoint) -> Unit) {
            endpoints.forEach(block)
        }

        @SubscribeEvent
        fun onMessage(event: GuildMessageReceivedEvent) {
            if (event.channel.id != channelId) return
            if (event.author.id == event.jda.selfUser.id) return

            forEachEndpoint {
                it.sendDiscordMessage(event.message)
            }
        }
    })
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
            RelayConnectedEndpoint.Discord(
                jda = connectionContext.jda,
                channelId = ircConnection.discordChannelId,
            ),
            RelayConnectedEndpoint.Irc(
                client = connectionContext.ircClientMap.getByName(ircConnection.ircServerName),
                channelName = ircConnection.ircChannelName,
                config = IrcRelaySourceConfig(
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
