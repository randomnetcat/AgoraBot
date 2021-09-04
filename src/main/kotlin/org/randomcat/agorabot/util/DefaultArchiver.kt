package org.randomcat.agorabot.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageType
import org.randomcat.agorabot.commands.DiscordArchiver
import java.io.BufferedWriter
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneOffset.UTC)

private fun writeMessageTextTo(
    message: Message,
    attachmentNumbers: List<BigInteger>,
    out: BufferedWriter,
) {
    val attachmentLines = attachmentNumbers.map {
        "Attachment $it"
    }

    val contentPart = if (message.contentDisplay.isBlank()) {
        if (attachmentLines.isNotEmpty()) {
            "This message consists only of attachments:\n$attachmentLines"
        } else {
            "<no content>"
        }
    } else {
        message.contentRaw +
                if (attachmentLines.isNotEmpty())
                    "\n\nThis message has attachments:\n$attachmentLines"
                else
                    ""
    }

    val instantCreated = message.timeCreated.toInstant()

    out.write(
        "MESSAGE ${message.id}\n" +
                "FROM ${message.author.name} " +
                "IN #${message.channel.name} " +
                "ON ${DATE_FORMAT.format(instantCreated)} " +
                "AT ${TIME_FORMAT.format(instantCreated)}:" +
                "\n" +
                contentPart +
                "\n\n"
    )
}

private data class PendingAttachmentDownload(
    val attachment: Message.Attachment,
    val attachmentNumber: BigInteger,
)

private suspend fun writeAttachmentContentTo(attachment: Message.Attachment, out: OutputStream) {
    attachment.retrieveInputStream().await().use { downloadStream ->
        downloadStream.copyTo(out)
    }
}

private fun openTextWriter(textPath: Path): BufferedWriter {
    return Files.newOutputStream(textPath, StandardOpenOption.CREATE_NEW).bufferedWriter(charset = Charsets.UTF_8)
}

private suspend fun receivePendingDownloads(
    attachmentChannel: Channel<PendingAttachmentDownload>,
    zipOut: ZipOutputStream,
) {
    for (pendingDownload in attachmentChannel) {
        val number = pendingDownload.attachmentNumber
        val fileName = pendingDownload.attachment.fileName

        zipOut.putNextEntry(ZipEntry("attachments/attachment-${number}/${fileName}"))
        writeAttachmentContentTo(pendingDownload.attachment, zipOut)
    }
}

private fun completeZipFile(zipOut: ZipOutputStream, textPath: Path) {
    zipOut.putNextEntry(ZipEntry("messages.txt"))

    Files.newInputStream(textPath).use { textIn ->
        textIn.copyTo(zipOut)
    }
}

private suspend fun receiveMessages(
    messageChannel: ReceiveChannel<Message>,
    attachmentChannel: SendChannel<PendingAttachmentDownload>,
    textOut: BufferedWriter,
) {
    var currentAttachmentNumber = BigInteger.ZERO

    for (message in messageChannel) {
        if (message.type != MessageType.DEFAULT) continue

        val attachmentNumbers = message.attachments.map {
            val number = ++currentAttachmentNumber
            attachmentChannel.send(PendingAttachmentDownload(it, number))

            number
        }

        writeMessageTextTo(message, attachmentNumbers, textOut)
    }
}

class DefaultDiscordArchiver(
    private val storageDir: Path,
) : DiscordArchiver {
    init {
        // Clean out any old archives
        deleteRecursively(storageDir)
        Files.createDirectories(storageDir)
    }

    private val archiveCount = AtomicReference(BigInteger.ZERO)

    override suspend fun createArchiveFrom(
        channel: MessageChannel,
    ): Path {
        val archiveNumber = archiveCount.getAndUpdate { it + BigInteger.ONE }

        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(Dispatchers.IO) {
            val workDir = storageDir.resolve("archive-$archiveNumber")
            Files.createDirectory(workDir)
            val textPath = workDir.resolve("messages.txt")

            val outPath = storageDir.resolve("archive-output-$archiveNumber")

            ZipOutputStream(Files.newOutputStream(outPath)).use { zipOut ->
                val attachmentChannel = Channel<PendingAttachmentDownload>(capacity = 100)

                coroutineScope {
                    launch {
                        receivePendingDownloads(attachmentChannel, zipOut)
                    }

                    launch {
                        openTextWriter(textPath).use { textOut ->
                            val messageChannel = forwardHistoryChannelOf(channel, bufferCapacity = 500)
                            receiveMessages(messageChannel, attachmentChannel, textOut)
                        }
                    }.also {
                        it.invokeOnCompletion { cause ->
                            attachmentChannel.close(cause = cause)
                        }
                    }
                }

                completeZipFile(zipOut, textPath)
            }

            outPath
        }
    }

    override val archiveExtension: String
        get() = "zip"
}
