package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.commands.impl.BaseCommandOutputSink
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation


/**
 * An output sink that sends command output to IRC.
 *
 * @param channelMap a map of Discord channel ids to irc channels.
 */
data class BaseCommandIrcOutputSink(
    private val channelMap: ImmutableMap<String, () -> IrcChannel?>,
) : BaseCommandOutputSink {
    constructor(channelMap: Map<String, () -> IrcChannel?>) : this(channelMap.toImmutableMap())

    private fun channelForEvent(source: CommandEventSource): IrcChannel? {
        return when (source) {
            is CommandEventSource.Discord -> channelMap[source.event.channel.id]?.invoke()
        }
    }

    override fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String) {
        channelForEvent(source)?.run {
            sendSplitMultiLineMessage(message)
        }
    }

    override fun sendResponseMessage(source: CommandEventSource, invocation: CommandInvocation, message: Message) {
        channelForEvent(source)?.run {
            sendSplitMultiLineMessage(message.contentRaw)
        }
    }

    override fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        channelForEvent(source)?.run {
            sendIllegalAttachmentMessage(fileName)
        }
    }

    override fun sendResponseTextAndFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    ) {
        channelForEvent(source)?.run {
            sendSplitMultiLineMessage(textResponse)
            sendIllegalAttachmentMessage(fileName)
        }
    }

    companion object {
        private fun IrcChannel.sendIllegalAttachmentMessage(fileName: String) {
            val safeFileName = fileName.lineSequence().joinToString("") // Paranoia

            sendMultiLineMessage(
                "Well, I *would* send an attachment, and it *would* have been called \"$safeFileName\", " +
                        "but this is a lame forum that doesn't support attachments, so all you get is this message."
            )
        }
    }
}
