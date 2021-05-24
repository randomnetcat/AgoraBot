package org.randomcat.agorabot.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

interface IrcMessageHandler {
    fun onMessage(event: ChannelMessageEvent) {}
    fun onCtcpMessage(event: ChannelCtcpEvent) {}
}

class IrcListener(private val messageHandler: IrcMessageHandler) {
    @Handler
    fun onMessageReceived(event: ChannelMessageEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onMessage(event)
    }

    @Handler
    fun onCtcpMessageReceiver(event: ChannelCtcpEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onCtcpMessage(event)
    }
}
