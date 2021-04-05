package org.randomcat.agorabot.util

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
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val ARCHIVE_BATCH_SIZE = 100

private fun writeMessageTextTo(
    message: Message,
    attachmentNumbers: List<BigInteger>,
    out: BufferedWriter,
) {
    val attachmentLines = attachmentNumbers.map {
        "Attachment $it"
    }

    val contentPart = if (message.contentRaw.isBlank()) {
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

    val adjustedTimeCreated = message.timeCreated.utcLocalDateTime()

    out.write(
        "MESSAGE ${message.id}\n" +
                "FROM ${message.author.name} " +
                "IN #${message.channel.name} " +
                "ON ${DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedTimeCreated)} " +
                "AT ${DateTimeFormatter.ISO_LOCAL_TIME.format(adjustedTimeCreated)}:" +
                "\n" +
                contentPart +
                "\n\n"
    )
}

class DefaultDiscordArchiver(private val storageDir: Path) : DiscordArchiver {
    private class ArchiveJobState(workDir: Path, private val outFile: Path) {
        init {
            Files.createDirectories(workDir)
        }

        private var currentAttachmentNumber = BigInteger.ZERO

        private val attachmentsDir = workDir.resolve("attachments")

        init {
            Files.createDirectory(attachmentsDir)
        }

        private val textPath = workDir.resolve("messages.txt")

        private fun nextAttachmentNumber() = ++currentAttachmentNumber

        private fun writeAttachmentContentTo(attachment: Message.Attachment, out: OutputStream) {
            attachment.retrieveInputStream().get().use { downloadStream ->
                downloadStream.copyTo(out)
            }
        }

        private fun writeBatch(messages: List<Message>, textOut: BufferedWriter, attachmentOut: ZipOutputStream) {
            for (message in messages) {
                if (message.type != MessageType.DEFAULT) continue

                val attachmentNumbers = message.attachments.map {
                    val number = nextAttachmentNumber()
                    attachmentOut.putNextEntry(ZipEntry("attachments/attachment-${number}/${it.fileName}"))
                    writeAttachmentContentTo(it, attachmentOut)

                    number
                }

                writeMessageTextTo(message, attachmentNumbers, textOut)
            }
        }

        fun createArchiveOn(
            executor: ExecutorService,
            messageIterator: Iterator<Message>,
        ): CompletionStage<Result<Path>> {
            val textOut =
                Files.newOutputStream(textPath, StandardOpenOption.CREATE_NEW).bufferedWriter(charset = Charsets.UTF_8)

            val zipOut = ZipOutputStream(Files.newOutputStream(outFile))

            val result = CompletableFuture<Unit>()
            writeBatchAndScheduleNext(
                executor = executor,
                messageIterator = messageIterator,
                textOut = textOut,
                attachmentOut = zipOut,
                result = result,
            )

            return result
                .thenApplyAsync(
                    {
                        textOut.close()

                        try {
                            completeZipFile(zipOut)
                            Result.success(outFile)
                        } finally {
                            zipOut.close()
                        }
                    },
                    executor,
                )
                .exceptionallyAsync(
                    {
                        // If these were already closed, it's fine to close them again (and will have no effect under
                        // the Closeable interface).
                        textOut.close()
                        zipOut.close()

                        Result.failure(it)
                    },
                    executor,
                )
        }

        private fun writeBatchAndScheduleNext(
            executor: ExecutorService,
            messageIterator: Iterator<Message>,
            textOut: BufferedWriter,
            attachmentOut: ZipOutputStream,
            result: CompletableFuture<Unit>,
        ) {
            try {
                executor.submit {
                    try {
                        val (currentBatch, nextIterator) = messageIterator.consumeFirst(ARCHIVE_BATCH_SIZE)

                        writeBatch(
                            messages = currentBatch,
                            textOut = textOut,
                            attachmentOut = attachmentOut,
                        )

                        if (nextIterator != null) {
                            executor.submit {
                                writeBatchAndScheduleNext(
                                    executor = executor,
                                    messageIterator = messageIterator,
                                    textOut = textOut,
                                    attachmentOut = attachmentOut,
                                    result = result,
                                )
                            }
                        } else {
                            result.complete(Unit)
                        }
                    } catch (e: Exception) {
                        result.completeExceptionally(e)
                    }
                }
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }

        private fun completeZipFile(zipOut: ZipOutputStream) {
            zipOut.putNextEntry(ZipEntry("messages.txt"))

            Files.newInputStream(textPath).use { textIn ->
                textIn.copyTo(zipOut)
            }
        }
    }

    init {
        // Clean out any old archives
        deleteRecursively(storageDir)
        Files.createDirectories(storageDir)
    }

    private val archiveCount = AtomicReference(BigInteger.ZERO)

    override fun createArchiveFromAsync(
        executor: ExecutorService,
        channel: MessageChannel,
    ): CompletionStage<Result<Path>> {
        return doCreateArchive(executor, channel.forwardHistorySequence().iterator())
    }

    private fun doCreateArchive(
        executor: ExecutorService,
        messageIterator: Iterator<Message>,
    ): CompletionStage<Result<Path>> {
        val archiveNumber = archiveCount.getAndUpdate { it + BigInteger.ONE }

        val workDir = storageDir.resolve("archive-$archiveNumber")
        val outPath = storageDir.resolve("archive-output-$archiveNumber")

        return ArchiveJobState(workDir = workDir, outFile = outPath).createArchiveOn(executor, messageIterator)
    }

    override val archiveExtension: String
        get() = "zip"
}
