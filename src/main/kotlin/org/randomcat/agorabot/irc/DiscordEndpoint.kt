package org.randomcat.agorabot.irc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.dv8tion.jda.api.utils.SplitUtil
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.disallowMentions
import org.randomcat.agorabot.util.retrieveEffectiveSenderName
import org.slf4j.LoggerFactory

private fun formatRawNameForDiscord(name: String): String {
    return "**$name**"
}

private val logger = LoggerFactory.getLogger("RelayDiscord")

private fun addDiscordRelay(
    jda: JDA,
    coroutineScope: CoroutineScope,
    channelId: String,
    endpoints: List<RelayConnectedEndpoint>,
) {
    jda.addEventListener(object {
        private inline fun forEachEndpoint(block: (RelayConnectedEndpoint) -> Unit) {
            endpoints.forEach(block)
        }

        @SubscribeEvent
        fun onMessage(event: MessageReceivedEvent) {
            if (event.channel.id != channelId) return
            if (event.author.id == event.jda.selfUser.id) return

            coroutineScope.launch {
                forEachEndpoint {
                    launch {
                        try {
                            it.sendDiscordMessage(event.message)
                        } catch (e: Exception) {
                            logger.error("Error forwarding discord message: endpoint: $it, event: $it")
                        }
                    }
                }
            }
        }
    })
}

data class RelayConnectedDiscordEndpoint(
    val jda: JDA,
    val coroutineScope: CoroutineScope,
    val channelId: String,
) : RelayConnectedEndpoint() {
    companion object {
        private fun relayToChannel(channel: MessageChannel, text: String) {
            val parts = SplitUtil.split(text, Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)

            parts.forEach {
                channel
                    .sendMessage(it)
                    .disallowMentions()
                    .queue()
            }
        }
    }

    private fun tryGetChannel(): MessageChannel? {
        return jda.getTextChannelById(channelId)
    }

    private inline fun tryWithChannel(block: (MessageChannel) -> Unit) {
        tryGetChannel()?.let(block)
    }

    override suspend fun sendTextMessage(sender: String, content: String) {
        tryWithChannel { channel ->
            relayToChannel(channel, formatRawNameForDiscord(sender) + " says: " + content)
        }
    }

    override suspend fun sendSlashMeTextMessage(sender: String, action: String) {
        tryWithChannel { channel ->
            relayToChannel(channel, formatRawNameForDiscord(sender) + " " + action)
        }
    }

    override suspend fun sendDiscordMessage(message: DiscordMessage) {
        tryWithChannel { channel ->
            val senderName = message.retrieveEffectiveSenderName().await()

            val referencedMessage = message.referencedMessage

            val replySection = if (referencedMessage != null) {
                val replyName = referencedMessage.retrieveEffectiveSenderName().await()

                "In reply to ${formatRawNameForDiscord(replyName)} saying: ${referencedMessage.contentRaw}\n"
            } else {
                ""
            }

            val textSection = run {
                val saysVerb = if (referencedMessage != null) "replies" else "says"
                "${formatRawNameForDiscord(senderName)} $saysVerb: ${message.contentRaw}"
            }

            val attachmentsSection = if (message.attachments.isNotEmpty()) {
                "\n\nAttachments:\n${message.attachments.joinToString("\n") { it.url }}"
            } else {
                ""
            }

            relayToChannel(
                channel,
                "$replySection$textSection$attachmentsSection",
            )
        }
    }

    override fun registerSourceEventHandler(
        context: RelayEventHandlerContext,
        otherEndpoints: List<RelayConnectedEndpoint>,
    ) {
        addDiscordRelay(
            jda = jda,
            coroutineScope = coroutineScope,
            channelId = channelId,
            endpoints = otherEndpoints,
        )
    }

    override fun commandOutputSink(): CommandOutputSink? {
        return tryGetChannel()?.let { CommandOutputSink.Discord(it) }
    }
}
