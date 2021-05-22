package org.randomcat.agorabot.irc

import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.CommandOutputMapping
import org.randomcat.agorabot.commands.impl.BaseCommandOutputStrategy
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation


/**
 * An output strategy that sends command output to IRC.
 *
 * @param channelMap a map of Discord channel ids to irc channels.
 */
data class BaseCommandIrcOutputStrategy(
    private val outputMapping: CommandOutputMapping,
) : BaseCommandOutputStrategy {
    private fun channelForSource(source: CommandEventSource): IrcChannel? {
        return outputMapping.ircResponseChannelFor(source)
    }

    override fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String) {
        channelForSource(source)?.run {
            sendSplitMultiLineMessage(message)
        }
    }

    override fun sendResponseMessage(source: CommandEventSource, invocation: CommandInvocation, message: Message) {
        channelForSource(source)?.run {
            sendSplitMultiLineMessage(message.contentRaw)
        }
    }

    override fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        channelForSource(source)?.run {
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
        channelForSource(source)?.run {
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
