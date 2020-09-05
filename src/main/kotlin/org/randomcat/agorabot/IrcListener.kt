package org.randomcat.agorabot

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

interface IrcMessageHandler {
    fun onMessage(event: ChannelMessageEvent)
}

class IrcListener(private val messageHandler: IrcMessageHandler) {
    @Handler
    fun onMessageReceived(event: ChannelMessageEvent) {
        if (event.actor.nick == event.client.nick) return
        messageHandler.onMessage(event)
    }
}
