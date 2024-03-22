package org.randomcat.agorabot.irc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.helper.ActorEvent
import org.kitteh.irc.client.library.event.helper.ChannelEvent
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.retrieveEffectiveSenderName
import org.slf4j.LoggerFactory

data class IrcRelayEndpointConfig(
    val commandPrefix: String?,
)

private val logger = LoggerFactory.getLogger("RelayIrc")

private suspend fun IrcChannel.sendDiscordMessage(message: DiscordMessage) {
    val senderName = message.retrieveEffectiveSenderName().await()

    val referencedMessage = message.referencedMessage

    // Use contentDisplay to resolve mentions.

    val replySection = run {
        if (referencedMessage != null) {
            val replyName = referencedMessage.retrieveEffectiveSenderName().await()
            val cleanContent = formatRelayDiscordContent(referencedMessage.contentDisplay)

            val contentLines = cleanContent.lines()
            val ellipsis = if (contentLines.size > 1) "..." else ""

            "In response to $replyName saying: ${contentLines.first()}$ellipsis\n"
        } else {
            ""
        }
    }

    val attachmentSection =
        message
            .attachments
            .map { it.url }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n", prefix = "\n")
            ?: ""

    val saysVerb = if (referencedMessage != null) "replies" else "says"
    val textSection = "$senderName $saysVerb: ${formatRelayDiscordContent(message.contentDisplay)}"

    val fullMessage = replySection + textSection + attachmentSection

    sendSplitMultiLineMessage(fullMessage)
}

private fun ircCommandParser(config: IrcRelayEndpointConfig): CommandParser? {
    val prefix = config.commandPrefix ?: return null
    return GlobalPrefixCommandParser(prefix)
}

private fun addIrcRelay(
    coroutineScope: CoroutineScope,
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

            coroutineScope.launch {
                coroutineScope {
                    forEachEndpoint {
                        launch {
                            try {
                                it.sendTextMessage(sender = event.actor.nick, content = event.message)
                            } catch (e: Exception) {
                                logger.error("Error forwarding message: endpoint: $it, event: $it")
                            }
                        }
                    }
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
        }

        override fun onCtcpMessage(event: ChannelCtcpEvent) {
            if (!event.isInRelevantChannel()) return

            val message = event.message
            if (!message.startsWith("ACTION ")) return // ACTION means a /me command

            coroutineScope.launch {
                forEachEndpoint {
                    launch {
                        try {
                            it.sendSlashMeTextMessage(
                                sender = event.actor.nick,
                                action = message.removePrefix("ACTION "),
                            )
                        } catch (e: Exception) {
                            logger.error("Error forwarding discord message: endpoint: $it, event: $it")
                        }
                    }
                }
            }
        }
    }))
}

data class RelayConnectedIrcEndpoint(
    val coroutineScope: CoroutineScope,
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

    override suspend fun sendTextMessage(sender: String, content: String) {
        tryWithChannel { channel ->
            channel.sendSplitMultiLineMessage("$sender says: $content")
        }
    }

    override suspend fun sendSlashMeTextMessage(sender: String, action: String) {
        tryWithChannel { channel ->
            channel.sendSplitMultiLineMessage("$sender $action")
        }
    }

    override suspend fun sendDiscordMessage(message: DiscordMessage) {
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
            coroutineScope = coroutineScope,
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
