package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.entities.Message
import org.randomcat.agorabot.CommandOutputMapping
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.irc.sendSplitMultiLineMessage
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.util.disallowMentions

private fun CommandEventSource.selfSink(): CommandOutputSink {
    return when (this) {
        is CommandEventSource.Discord -> CommandOutputSink.Discord(event.channel)
        is CommandEventSource.Irc -> CommandOutputSink.Irc(event.channel)
    }
}

private fun CommandOutputSink.sendSimpleMessage(text: String) {
    return when (this) {
        is CommandOutputSink.Discord -> channel.sendMessage(text).disallowMentions().queue()
        is CommandOutputSink.Irc -> channel.sendSplitMultiLineMessage(text)
    }
}

private fun CommandOutputSink.sendDiscordMessage(message: Message) {
    return when (this) {
        is CommandOutputSink.Discord -> channel.sendMessage(message).disallowMentions().queue()
        is CommandOutputSink.Irc -> sendSimpleMessage(message.contentRaw)
    }
}

private fun CommandOutputSink.Irc.sendIllegalAttachmentMessage(fileName: String) {
    val safeFileName = fileName.lineSequence().joinToString("") // Paranoia

    channel.sendMultiLineMessage(
        "Well, I *would* send an attachment, and it *would* have been called \"$safeFileName\", " +
                "but this is a lame forum that doesn't support attachments, so all you get is this message."
    )
}

private fun CommandOutputSink.sendAttachmentMessage(fileName: String, fileContent: String) {
    return when (this) {
        is CommandOutputSink.Discord -> {
            val bytes = fileContent.toByteArray(Charsets.UTF_8)
            channel.sendFile(bytes, fileName).disallowMentions().queue()
        }

        is CommandOutputSink.Irc -> {
            sendIllegalAttachmentMessage(fileName = fileName)
        }
    }
}

private fun CommandOutputSink.sendTextAndAttachmentMessage(text: String, fileName: String, fileContent: String) {
    return when (this) {
        is CommandOutputSink.Discord -> {
            val bytes = fileContent.toByteArray(Charsets.UTF_8)
            channel.sendMessage(text).addFile(bytes, fileName).disallowMentions().queue()
        }

        is CommandOutputSink.Irc -> {
            sendSimpleMessage(text)
            sendIllegalAttachmentMessage(fileName = fileName)
        }
    }
}

data class BaseCommandOutputStrategyByOutputMapping(
    private val outputMapping: CommandOutputMapping,
) : BaseCommandOutputStrategy {
    private inline fun forEachSinkOf(source: CommandEventSource, block: (CommandOutputSink) -> Unit) {
        block(source.selfSink())
        outputMapping.externalSinksFor(source).forEach(block)
    }

    override fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String) {
        forEachSinkOf(source) { sink ->
            sink.sendSimpleMessage(message)
        }
    }

    override fun sendResponseMessage(source: CommandEventSource, invocation: CommandInvocation, message: Message) {
        forEachSinkOf(source) { sink ->
            sink.sendDiscordMessage(message)
        }
    }

    override fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        forEachSinkOf(source) { sink ->
            sink.sendAttachmentMessage(fileName = fileName, fileContent = fileContent)
        }
    }

    override fun sendResponseTextAndFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    ) {
        forEachSinkOf(source) { sink ->
            sink.sendTextAndAttachmentMessage(text = textResponse, fileName = fileName, fileContent = fileContent)
        }
    }
}
