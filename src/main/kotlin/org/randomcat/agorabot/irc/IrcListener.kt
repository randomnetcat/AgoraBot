package org.randomcat.agorabot.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.element.User
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.helper.ActorEvent

interface IrcMessageHandler {
    fun onMessage(event: ChannelMessageEvent)
    fun onJoin(event: ChannelJoinEvent)
    fun onLeave(event: ChannelPartEvent)
}

class IrcListener(private val messageHandler: IrcMessageHandler) {
    companion object {
        private fun ActorEvent<User>.isSelfEvent() = actor.nick == client.nick
    }

    @Handler
    fun onMessageReceived(event: ChannelMessageEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onMessage(event)
    }

    @Handler
    fun onJoinReceived(event: ChannelJoinEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onJoin(event)
    }

    @Handler
    fun onLeaveReceived(event: ChannelPartEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onLeave(event)
    }
}
