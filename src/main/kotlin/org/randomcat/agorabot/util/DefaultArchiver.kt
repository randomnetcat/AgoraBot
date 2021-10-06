package org.randomcat.agorabot.util

import jakarta.json.Json
import jakarta.json.JsonObject
import jakarta.json.stream.JsonGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
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
        add("type", message.type.name)
        add("author_id", message.author.id)
        add("raw_text", message.contentRaw)
        if (!message.type.isSystem) add("display_text", message.contentDisplay)

        message.messageReference?.let {
            add(
                "referenced_message",
                with(Json.createObjectBuilder()) {
                    add("guild_id", it.guildId)
                    add("channel_id", it.channelId)
                    add("message_id", it.messageId)

                    build()
                }
            )
        }

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
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    textOut: Writer,
    jsonOut: Writer,
) {
    var currentAttachmentNumber = BigInteger.ZERO

    Json.createGenerator(jsonOut.nonClosingView()).use { jsonGenerator ->
        jsonGenerator.writeStartMessages()

        for (message in messageChannel) {
            val attachmentNumbers = message.attachments.map {
                val number = ++currentAttachmentNumber
                attachmentChannel.send(PendingAttachmentDownload(it, number))

                number
            }

            globalDataChannel.send(
                ArchiveGlobalData.ReferencedUser(
                    id = message.author.id,
                ),
            )

            message.mentionedUsers.forEach {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedUser(
                        id = it.id,
                    ),
                )
            }

            message.mentionedRoles.forEach {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedRole(
                        id = it.id,
                    ),
                )
            }

            message.mentionedChannels.forEach {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedChannel(
                        id = it.id,
                    ),
                )
            }

            message.messageReference?.let {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedChannel(
                        id = it.channelId,
                    ),
                )
            }

            writeMessageTextTo(message, attachmentNumbers, textOut)
            jsonGenerator.writeMessage(message, attachmentNumbers)
        }

        jsonGenerator.writeEndMessages()
    }
}

private sealed class ArchiveGlobalData {
    data class ReferencedUser(val id: String) : ArchiveGlobalData()
    data class ReferencedRole(val id: String) : ArchiveGlobalData()
    data class ReferencedChannel(val id: String) : ArchiveGlobalData()
}

private suspend fun archiveChannel(
    channel: MessageChannel,
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    basePath: Path,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
        globalDataChannel.send(ArchiveGlobalData.ReferencedChannel(channel.id))

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
                            globalDataChannel = globalDataChannel,
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

private suspend fun receiveGlobalData(
    dataChannel: ReceiveChannel<ArchiveGlobalData>,
    guild: Guild,
    outPath: Path,
) {
    val userObjects = mutableMapOf<String, JsonObject?>()
    val roleObjects = mutableMapOf<String, JsonObject?>()
    val channelObjects = mutableMapOf<String, JsonObject?>()

    for (data in dataChannel) {
        when (data) {
            is ArchiveGlobalData.ReferencedChannel -> {
                channelObjects.computeIfAbsent(data.id) { id ->
                    guild.getGuildChannelById(id)?.let { channel ->
                        Json.createObjectBuilder().run {
                            add("name", channel.name)

                            build()
                        }
                    }
                }
            }

            is ArchiveGlobalData.ReferencedRole -> {
                roleObjects.computeIfAbsent(data.id) { id ->
                    guild.getRoleById(id)?.let { role ->
                        Json.createObjectBuilder().run {
                            add("name", role.name)

                            build()
                        }
                    }
                }
            }

            is ArchiveGlobalData.ReferencedUser -> {
                val id = data.id

                // Have to suspend in the body, so computeIfAbsent is not usable
                userObjects.getOrPut(id) {
                    val member = runCatching { guild.retrieveMemberById(id).await() }.getOrNull()
                    val nickname = member?.nickname
                    val user =
                        member?.user
                            ?: runCatching { guild.jda.retrieveUserById(id).await() }.getOrNull()
                            ?: return@getOrPut null

                    Json.createObjectBuilder().run {
                        add("username", user.name)

                        if (nickname != null) {
                            add("nickname", nickname)
                        }

                        build()
                    }
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
        outPath.bufferedWriter().use { reader ->
            // This with close the reader, but that's fine, since close is idempotent
            Json.createGenerator(reader).use { generator ->
                with(generator) {
                    writeStartObject() // start top-level
                    writeStartObject("users")

                    for ((id, userObject) in userObjects) {
                        if (userObject != null) {
                            write(id, userObject)
                        }
                    }

                    writeEnd() // end users
                    writeStartObject("roles")

                    for ((id, roleObject) in roleObjects) {
                        if (roleObject != null) {
                            write(id, roleObject)
                        }
                    }

                    writeEnd() // end roles
                    writeStartObject("channels")

                    for ((id, channelObject) in channelObjects) {
                        if (channelObject != null) {
                            write(id, channelObject)
                        }
                    }

                    writeEnd() // end channels
                    writeEnd() // end top-level
                }
            }
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
        guild: Guild,
        channelIds: Set<String>,
    ): Path {
        val archiveNumber = archiveCount.getAndUpdate { it + BigInteger.ONE }

        val workDir = storageDir.resolve("archive-$archiveNumber")
        val archivePath = workDir.resolve("generated-archive.zip")

        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            workDir.createDirectory()

            ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(archivePath, ZIP_FILE_SYSTEM_CREATE_OPTIONS).use { zipFs ->
                coroutineScope {
                    val globalDataChannel = Channel<ArchiveGlobalData>(capacity = 100)

                    launch {
                        receiveGlobalData(
                            dataChannel = globalDataChannel,
                            guild = guild,
                            outPath = zipFs.getPath("global_data.json"),
                        )
                    }

                    try {
                        coroutineScope {
                            for (channelId in channelIds) {
                                val channel = guild.getTextChannelById(channelId)

                                requireNotNull(channel) {
                                    "Invalid channel id: $channelId"
                                }

                                launch {
                                    val outDir = zipFs.getPath(channelId)
                                    outDir.createDirectory()

                                    archiveChannel(
                                        channel = channel,
                                        globalDataChannel = globalDataChannel,
                                        basePath = outDir,
                                    )
                                }
                            }
                        }
                    } finally {
                        globalDataChannel.close()
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
