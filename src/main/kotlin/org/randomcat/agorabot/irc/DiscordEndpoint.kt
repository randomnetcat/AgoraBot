package org.randomcat.agorabot.irc

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.disallowMentions
import org.randomcat.agorabot.util.effectiveSenderName

private fun formatRawNameForDiscord(name: String): String {
    return "**$name**"
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
        fun onMessage(event: MessageReceivedEvent) {
            if (event.channel.id != channelId) return
            if (event.author.id == event.jda.selfUser.id) return

            forEachEndpoint {
                it.sendDiscordMessage(event.message)
            }
        }
    })
}

data class RelayConnectedDiscordEndpoint(val jda: JDA, val channelId: String) : RelayConnectedEndpoint() {
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

    private fun tryGetChannel(): MessageChannel? {
        return jda.getTextChannelById(channelId)
    }

    private inline fun tryWithChannel(block: (MessageChannel) -> Unit) {
        tryGetChannel()?.let(block)
    }

    override fun sendTextMessage(sender: String, content: String) {
        tryWithChannel { channel ->
            relayToChannel(channel, formatRawNameForDiscord(sender) + " says: " + content)
        }
    }

    override fun sendSlashMeTextMessage(sender: String, action: String) {
        tryWithChannel { channel ->
            relayToChannel(channel, formatRawNameForDiscord(sender) + " " + action)
        }
    }

    override fun sendDiscordMessage(message: DiscordMessage) {
        tryWithChannel { channel ->
            val senderName = message.effectiveSenderName

            val referencedMessage = message.referencedMessage

            val replySection = if (referencedMessage != null) {
                val replyName = referencedMessage.effectiveSenderName

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
            channelId = channelId,
            endpoints = otherEndpoints,
        )
    }

    override fun commandOutputSink(): CommandOutputSink? {
        return tryGetChannel()?.let { CommandOutputSink.Discord(it) }
    }
}
