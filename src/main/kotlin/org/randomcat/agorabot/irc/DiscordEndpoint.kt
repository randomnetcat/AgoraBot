package org.randomcat.agorabot.irc

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.disallowMentions

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
        fun onMessage(event: GuildMessageReceivedEvent) {
            if (event.channel.id != channelId) return
            if (event.author.id == event.jda.selfUser.id) return

            forEachEndpoint {
                it.sendDiscordMessage(event.message)
            }
        }
    })
}

data class RelayConnectedDiscordEndpoint(private val jda: JDA, private val channelId: String) :
    RelayConnectedEndpoint() {
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

    override fun commandOutputSink(): CommandOutputSink? {
        return tryGetChannel()?.let { CommandOutputSink.Discord(it) }
    }
}
