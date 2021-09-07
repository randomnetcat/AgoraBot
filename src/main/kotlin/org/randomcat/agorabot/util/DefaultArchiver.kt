package org.randomcat.agorabot.util

import jakarta.json.Json
import jakarta.json.JsonArrayBuilder
import jakarta.json.JsonObject
import jakarta.json.JsonValue
import jakarta.json.stream.JsonGenerator
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
import java.io.OutputStream
import java.io.Writer
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.spi.FileSystemProvider
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneOffset.UTC)

private fun writeMessageTextTo(
    message: Message,
    attachmentNumbers: List<BigInteger>,
    out: Writer,
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

private suspend fun receivePendingDownloads(
    attachmentChannel: ReceiveChannel<PendingAttachmentDownload>,
    attachmentsDir: Path,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
        for (pendingDownload in attachmentChannel) {
            launch {
                val number = pendingDownload.attachmentNumber
                val fileName = pendingDownload.attachment.fileName

                val attachmentDir = attachmentsDir.resolve("attachment-$number")
                attachmentDir.createDirectory()

                val outPath = attachmentDir.resolve(fileName)

                outPath.outputStream(StandardOpenOption.CREATE_NEW).use { outStream ->
                    writeAttachmentContentTo(pendingDownload.attachment, outStream)
                }
            }
        }
    }
}

private fun JsonGenerator.writeStartMessages() {
    writeStartObject()
    writeKey("messages")
    writeStartObject()
}

private fun JsonGenerator.writeEndMessages() {
    writeEnd() // End messages object
    writeEnd() // End top-level object
}

private fun JsonGenerator.writeMessage(message: Message, attachmentNumbers: List<BigInteger>) {
    val messageObject = with(Json.createObjectBuilder()) {
        add("author_id", message.author.id)
        add("text", message.contentRaw)

        add("instant_created", DateTimeFormatter.ISO_INSTANT.format(message.timeCreated))

        message.timeEdited?.let { timeEdited ->
            add("instant_edited", DateTimeFormatter.ISO_INSTANT.format(timeEdited))
        }

        add(
            "attachment_numbers",
            if (attachmentNumbers.isNotEmpty()) {
                Json
                    .createArrayBuilder()
                    .apply {
                        attachmentNumbers.forEach(this::add)
                    }
                    .build()
            } else {
                JsonObject.EMPTY_JSON_ARRAY
            },
        )

        build()
    }

    write(
        message.id,
        messageObject,
    )
}

private suspend fun receiveMessages(
    messageChannel: ReceiveChannel<Message>,
    attachmentChannel: SendChannel<PendingAttachmentDownload>,
    textOut: Writer,
    jsonOut: Writer,
) {
    var currentAttachmentNumber = BigInteger.ZERO

    Json.createGenerator(jsonOut.nonClosingView()).use { jsonGenerator ->
        jsonGenerator.writeStartMessages()

        for (message in messageChannel) {
            if (message.type != MessageType.DEFAULT) continue

            val attachmentNumbers = message.attachments.map {
                val number = ++currentAttachmentNumber
                attachmentChannel.send(PendingAttachmentDownload(it, number))

                number
            }

            writeMessageTextTo(message, attachmentNumbers, textOut)
            jsonGenerator.writeMessage(message, attachmentNumbers)
        }

        jsonGenerator.writeEndMessages()
    }
}

private suspend fun archiveChannel(channel: MessageChannel, basePath: Path) {
    coroutineScope {
        val attachmentChannel = Channel<PendingAttachmentDownload>(capacity = 10)

        launch {
            val textPath = basePath.resolve("messages.txt")
            val jsonPath = basePath.resolve("messages.json")

            try {
                textPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW)).use { textOut ->
                    jsonPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW)).use { jsonOut ->
                        receiveMessages(
                            messageChannel = forwardHistoryChannelOf(channel, bufferCapacity = 100),
                            attachmentChannel = attachmentChannel,
                            textOut = textOut,
                            jsonOut = jsonOut,
                        )
                    }
                }
            } finally {
                attachmentChannel.close()
            }
        }

        launch {
            val attachmentsDir = basePath.resolve("attachments")
            attachmentsDir.createDirectory()

            receivePendingDownloads(
                attachmentChannel = attachmentChannel,
                attachmentsDir = attachmentsDir,
            )
        }
    }
}

private val ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders().single {
    it.scheme.equals("jar", ignoreCase = true)
}

private val ZIP_FILE_SYSTEM_CREATE_OPTIONS = mapOf("create" to "true")

class DefaultDiscordArchiver(
    private val storageDir: Path,
) : DiscordArchiver {
    init {
        // Clean out any old archives
        deleteRecursively(storageDir)
        storageDir.createDirectories()
    }

    private val archiveCount = AtomicReference(BigInteger.ZERO)

    override suspend fun createArchiveFrom(
        channels: List<MessageChannel>,
    ): Path {
        val archiveNumber = archiveCount.getAndUpdate { it + BigInteger.ONE }

        val workDir = storageDir.resolve("archive-$archiveNumber")
        val archivePath = workDir.resolve("generated-archive.zip")

        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            workDir.createDirectory()

            ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(archivePath, ZIP_FILE_SYSTEM_CREATE_OPTIONS).use { zipFs ->
                coroutineScope {
                    for (channel in channels) {
                        launch {
                            val outDir = zipFs.getPath(channel.id)
                            outDir.createDirectory()

                            archiveChannel(
                                channel = channel,
                                basePath = outDir,
                            )
                        }
                    }
                }
            }
        }

        check(archivePath.isRegularFile())

        return archivePath
    }

    override val archiveExtension: String
        get() = "zip"
}
