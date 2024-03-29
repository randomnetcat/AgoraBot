package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.randomcat.agorabot.CommandOutputMapping
import org.randomcat.agorabot.CommandOutputSink
import org.randomcat.agorabot.commands.base.BaseCommandOutputStrategy
import org.randomcat.agorabot.irc.sendSplitMultiLineMessage
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.util.disallowMentions
import org.slf4j.LoggerFactory

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

private fun CommandOutputSink.sendDiscordMessage(message: MessageCreateData) {
    return when (this) {
        is CommandOutputSink.Discord -> channel.sendMessage(message).disallowMentions().queue()
        is CommandOutputSink.Irc -> sendSimpleMessage(message.content)
    }
}

private fun CommandOutputSink.Irc.sendIllegalAttachmentMessage(fileName: String) {
    val safeFileName = fileName.lineSequence().joinToString("") // Paranoia

    channel.sendMultiLineMessage(
        "Well, I *would* send an attachment, and it *would* have been called \"$safeFileName\", " +
                "but this is a forum that doesn't support attachments, so all you get is this message."
    )
}

private fun CommandOutputSink.sendAttachmentMessage(fileName: String, fileContent: String) {
    return when (this) {
        is CommandOutputSink.Discord -> {
            val bytes = fileContent.toByteArray(Charsets.UTF_8)
            channel.sendFiles(FileUpload.fromData(bytes, fileName)).disallowMentions().queue()
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
            val message = MessageCreateBuilder().setContent(text).addFiles(FileUpload.fromData(bytes, fileName))
                .disallowMentions().build()
            channel.sendMessage(message).queue()
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
    companion object {
        private val logger = LoggerFactory.getLogger(BaseCommandOutputStrategyByOutputMapping::class.java)
    }

    private inline fun forEachSinkOf(source: CommandEventSource, block: (CommandOutputSink) -> Unit) {
        block(source.selfSink())
        outputMapping.externalSinksFor(source).forEach(block)
    }

    override suspend fun sendResponse(source: CommandEventSource, invocation: CommandInvocation, message: String) {
        forEachSinkOf(source) { sink ->
            try {
                sink.sendSimpleMessage(message)
            } catch (e: Exception) {
                logger.error(
                    "Error sending command output: source = $source, invocation = $invocation, message = $message",
                    e
                )
            }
        }
    }

    override suspend fun sendResponseMessage(
        source: CommandEventSource,
        invocation: CommandInvocation,
        message: MessageCreateData,
    ) {
        forEachSinkOf(source) { sink ->
            try {
                sink.sendDiscordMessage(message)
            } catch (e: Exception) {
                logger.error(
                    "Error sending command output: source = $source, invocation = $invocation, message = $message",
                    e
                )
            }
        }
    }

    override suspend fun sendResponseAsFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        fileName: String,
        fileContent: String,
    ) {
        forEachSinkOf(source) { sink ->
            try {
                sink.sendAttachmentMessage(fileName = fileName, fileContent = fileContent)
            } catch (e: Exception) {
                logger.error(
                    "Error sending command file output: source = $source, invocation = $invocation, fileName = $fileName, fileContent = $fileContent",
                    e
                )
            }
        }
    }

    override suspend fun sendResponseTextAndFile(
        source: CommandEventSource,
        invocation: CommandInvocation,
        textResponse: String,
        fileName: String,
        fileContent: String,
    ) {
        forEachSinkOf(source) { sink ->
            try {
                sink.sendTextAndAttachmentMessage(text = textResponse, fileName = fileName, fileContent = fileContent)
            } catch (e: Exception) {
                logger.error(
                    "Error sending command output: source = $source, invocation = $invocation, text = $textResponse, fileName = $fileName, fileContent = $fileContent",
                    e
                )
            }
        }
    }
}
