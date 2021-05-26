package org.randomcat.agorabot.irc

import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.helper.ActorEvent
import org.kitteh.irc.client.library.event.helper.ChannelEvent
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.util.DiscordMessage

data class IrcRelayEndpointConfig(
    val commandPrefix: String?,
)

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

private fun ircCommandParser(config: IrcRelayEndpointConfig): CommandParser? {
    val prefix = config.commandPrefix ?: return null
    return GlobalPrefixCommandParser(prefix)
}

private fun addIrcRelay(
    ircClient: IrcClient,
    ircChannelName: String,
    config: IrcRelayEndpointConfig,
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

data class RelayConnectedIrcEndpoint(
    val client: IrcClient,
    val channelName: String,
    private val config: IrcRelayEndpointConfig,
) : RelayConnectedEndpoint() {
    private fun tryGetChannel(): Channel? {
        return client.getChannel(channelName).orElse(null)
    }

    private inline fun tryWithChannel(block: (IrcChannel) -> Unit) {
        tryGetChannel()?.let(block)
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

    override fun commandOutputSink(): CommandOutputSink? {
        return tryGetChannel()?.let { CommandOutputSink.Irc(it) }
    }
}
