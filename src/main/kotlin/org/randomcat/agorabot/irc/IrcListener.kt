package org.randomcat.agorabot.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.*
import org.kitteh.irc.client.library.event.user.UserQuitEvent

interface IrcMessageHandler {
    fun onMessage(event: ChannelMessageEvent)
    fun onCtcpMessage(event: ChannelCtcpEvent)
    fun onJoin(event: ChannelJoinEvent)
    fun onPart(event: ChannelPartEvent)
    fun onUnexpectedPart(event: UnexpectedChannelLeaveViaPartEvent)
    fun onQuit(event: UserQuitEvent)
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

    @Handler
    fun onJoinReceived(event: ChannelJoinEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onJoin(event)
    }

    @Handler
    fun onLeaveReceived(event: ChannelPartEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onPart(event)
    }

    @Handler
    fun onUnexpectedLeaveReceived(event: UnexpectedChannelLeaveViaPartEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onUnexpectedPart(event)
    }

    @Handler
    fun onQuitReceived(event: UserQuitEvent) {
        if (event.isSelfEvent()) return
        messageHandler.onQuit(event)
    }
}
