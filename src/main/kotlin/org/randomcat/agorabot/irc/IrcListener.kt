package org.randomcat.agorabot.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.channel.UnexpectedChannelLeaveViaPartEvent

interface IrcMessageHandler {
    fun onMessage(event: ChannelMessageEvent)
    fun onJoin(event: ChannelJoinEvent)
    fun onLeave(event: ChannelPartEvent)
    fun onUnexpectedLeave(event: UnexpectedChannelLeaveViaPartEvent)
}

class IrcListener(private val messageHandler: IrcMessageHandler) {
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

    @Handler
    fun onUnexpectedLeaveReceived(event: UnexpectedChannelLeaveViaPartEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onUnexpectedLeave(event)
    }
}
