@file:OptIn(ExperimentalTypeInference::class)

package org.randomcat.agorabot.util

import jakarta.json.*
import jakarta.json.stream.JsonGenerator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.channel.attribute.*
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.randomcat.agorabot.commands.DiscordArchiver
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.Writer
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.ExperimentalTypeInference
import kotlin.io.path.*

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneOffset.UTC)

private val logger = LoggerFactory.getLogger("DefaultArchiver")

private suspend fun <T> withChannel(
    capacity: Int,
    send: suspend (SendChannel<T>) -> Unit,
    receive: suspend (ReceiveChannel<T>) -> Unit,
) {
    val channel = Channel<T>(capacity = capacity)

    suspend fun ensureClosed(block: suspend () -> Unit) {
        var exception: Exception? = null

        try {
            block()
        } catch (e: Exception) {
            exception = e
            throw e
        } finally {
            if (exception == null) {
                channel.close()
            } else {
                try {
                    channel.close(exception)
                } catch (closeExcept: Exception) {
                    exception.addSuppressed(closeExcept)
                }
            }
        }
    }

    ensureClosed {
        coroutineScope {
            launch {
                ensureClosed {
                    send(channel)
                }
            }

            launch {
                receive(channel)
            }
        }
    }
}

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

private data class PendingReactionInfo(
    val messageId: String,
    val reactions: ImmutableList<MessageReaction>,
)

private suspend fun writeAttachmentContentTo(attachment: Message.Attachment, out: OutputStream) {
    attachment.proxy.download().await().use { downloadStream ->
        downloadStream.copyTo(out)
    }
}

private suspend fun receivePendingDownloads(
    attachmentChannel: ReceiveChannel<PendingAttachmentDownload>,
    attachmentsDir: Path,
) {
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

private suspend fun receiveReactions(
    reactionChannel: ReceiveChannel<PendingReactionInfo>,
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    reactionOut: Writer,
) {
    Json.createGenerator(reactionOut.nonClosingView()).use { generator ->
        generator.writeStartObject()
        generator.writeKey("messages")
        generator.writeStartObject()

        withContext(Dispatchers.IO) {
            for (reactionInfo in reactionChannel) {
                generator.writeKey(reactionInfo.messageId)
                generator.writeStartObject()

                generator.writeKey("reactions")
                generator.writeStartArray()

                val reactions = reactionInfo.reactions
                val reactionUsers = reactions.map { it.retrieveUsers().submit().asDeferred() }.awaitAll()

                for ((emoji, users) in (reactions zip reactionUsers)) {
                    generator.writeStartObject()

                    generator.write("emoji", emoji.emoji.asReactionCode)
                    generator.write("user_count", users.size)

                    generator.writeKey("users")
                    generator.writeStartArray()

                    for (user in users) {
                        generator.write(user.id)
                        globalDataChannel.send(ArchiveGlobalData.ReferencedUser(id = user.id))
                    }

                    generator.writeEnd()
                    generator.writeEnd()
                }

                generator.writeEnd()
                generator.writeEnd()
            }
        }

        generator.writeEnd()
        generator.writeEnd()
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

private inline fun buildJsonObject(builder: JsonObjectBuilder.() -> Unit): JsonObject {
    return Json.createObjectBuilder().apply(builder).build()
}

private inline fun buildJsonArray(builder: JsonArrayBuilder.() -> Unit): JsonArray {
    return Json.createArrayBuilder().apply(builder).build()
}

@OverloadResolutionByLambdaReturnType
@JvmName("mapToJsonArrayOfString")
private inline fun <T> Collection<T>.mapToJsonArray(@BuilderInference mapper: (T) -> String): JsonArray {
    if (isEmpty()) return JsonValue.EMPTY_JSON_ARRAY

    return buildJsonArray {
        forEach { add(mapper(it)) }
    }
}

@OverloadResolutionByLambdaReturnType
@JvmName("mapToJsonArrayOfObject")
private inline fun <T> Collection<T>.mapToJsonArray(mapper: (T) -> JsonObject): JsonArray {
    if (isEmpty()) return JsonValue.EMPTY_JSON_ARRAY

    return buildJsonArray {
        forEach { add(mapper(it)) }
    }
}

@OverloadResolutionByLambdaReturnType
@JvmName("mapToJsonArrayOfObjectBuilder")
private inline fun <T> Collection<T>.mapToJsonArray(mapper: (T) -> JsonObjectBuilder): JsonArray {
    return mapToJsonArray { mapper(it).build() }
}

private fun Collection<BigInteger>.toJsonArray(): JsonArray {
    if (isEmpty()) return JsonValue.EMPTY_JSON_ARRAY

    return buildJsonArray {
        forEach(this::add)
    }
}

private fun JsonGenerator.writeMessage(message: Message, attachmentNumbers: List<BigInteger>) {
    val messageObject = buildJsonObject {
        add("type", message.type.name)
        add("author_id", message.author.id)
        add("raw_text", message.contentRaw)
        if (!message.type.isSystem) add("display_text", message.contentDisplay)

        message.messageReference?.let {
            add(
                "referenced_message",
                buildJsonObject {
                    add("guild_id", it.guildId)
                    add("channel_id", it.channelId)
                    add("message_id", it.messageId)
                }
            )
        }

        add("instant_created", DateTimeFormatter.ISO_INSTANT.format(message.timeCreated))

        message.timeEdited?.let { timeEdited ->
            add("instant_edited", DateTimeFormatter.ISO_INSTANT.format(timeEdited))
        }

        add("is_edited", message.isEdited)
        add("is_ephemeral", message.isEphemeral)
        add("is_webhook_message", message.isWebhookMessage)
        add("is_tts", message.isTTS)
        add("is_pinned", message.isPinned)
        add("flags", message.flags.mapToJsonArray { it.name })

        message.applicationId?.let { add("application_id", it) }

        message.interaction?.let {
            add("interaction", buildJsonObject {
                add("id", it.id)
                add("name", it.name)
                add("type", it.type.name)
                add("user_id", it.user.id)
            })
        }

        add("mentions", buildJsonObject {
            add("user_ids", message.mentions.usersBag.mapToJsonArray { it.id })
            add("role_ids", message.mentions.rolesBag.mapToJsonArray { it.id })
            add("channel_ids", message.mentions.channelsBag.mapToJsonArray { it.id })
        })

        message.startedThread?.id?.let { add("started_thread_id", it) }

        message.activity?.let { activity ->
            add("activity", buildJsonObject {
                add("type", activity.type.name)
                add("party_id", activity.partyId)

                activity.application?.let { app ->
                    add("application", buildJsonObject {
                        add("id", app.id)
                        add("name", app.name)
                        add("description", app.description)
                    })
                }
            })
        }

        add(
            "attachment_numbers",
            attachmentNumbers.toJsonArray(),
        )

        add("stickers", message.stickers.mapToJsonArray { sticker ->
            buildJsonObject {
                add("id", sticker.id)
                add("name", sticker.name)
                add("instant_created", DateTimeFormatter.ISO_INSTANT.format(sticker.timeCreated))
                add("icon_url", sticker.iconUrl)
                add("format_type", sticker.formatType.name)
            }
        })

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
    reactionChannel: SendChannel<PendingReactionInfo>,
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

            message.mentions.usersBag.forEach {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedUser(
                        id = it.id,
                    ),
                )
            }

            message.mentions.rolesBag.forEach {
                globalDataChannel.send(
                    ArchiveGlobalData.ReferencedRole(
                        id = it.id,
                    ),
                )
            }

            message.mentions.channelsBag.forEach {
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

            val reactions = message.reactions

            if (reactions.isNotEmpty()) {
                reactionChannel.send(
                    PendingReactionInfo(
                        messageId = message.id,
                        reactions = reactions.toImmutableList(),
                    ),
                )
            }
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
    globalDataChannel.send(ArchiveGlobalData.ReferencedChannel(channel.id))

    coroutineScope {
        launch(Dispatchers.IO) {
            withChannel<PendingAttachmentDownload>(
                capacity = 10,
                send = { attachmentChannel ->
                    withChannel<PendingReactionInfo>(
                        capacity = 100,
                        send = { reactionChannel ->
                            val textPath = basePath.resolve("messages.txt")
                            val jsonPath = basePath.resolve("messages.json")

                            textPath
                                .bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW))
                                .use { textOut ->
                                    jsonPath
                                        .bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW))
                                        .use { jsonOut ->
                                            withChannel<DiscordMessage>(
                                                capacity = 100,
                                                send = { messageChannel ->
                                                    channel.sendForwardHistoryTo(messageChannel)
                                                },
                                                receive = { messageChannel ->
                                                    receiveMessages(
                                                        messageChannel = messageChannel,
                                                        attachmentChannel = attachmentChannel,
                                                        reactionChannel = reactionChannel,
                                                        globalDataChannel = globalDataChannel,
                                                        textOut = textOut,
                                                        jsonOut = jsonOut,
                                                    )
                                                }
                                            )
                                        }
                                }
                        },
                        receive = { reactionChannel ->
                            val reactionsPath = basePath.resolve("reactions.json")

                            reactionsPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW))
                                .use { reactionOut ->
                                    receiveReactions(
                                        reactionChannel = reactionChannel,
                                        globalDataChannel = globalDataChannel,
                                        reactionOut = reactionOut,
                                    )
                                }
                        },
                    )
                },
                receive = { attachmentChannel ->
                    val attachmentsDir = basePath.resolve("attachments")
                    attachmentsDir.createDirectory()

                    receivePendingDownloads(
                        attachmentChannel = attachmentChannel,
                        attachmentsDir = attachmentsDir,
                    )
                }
            )
        }

        if (channel is IThreadContainer) {
            launch(Dispatchers.Default) {
                val threadChannels = buildList<ThreadChannel> {
                    addAll(channel.threadChannels)
                    addAll(channel.retrieveArchivedPublicThreadChannels().await())
                    addAll(channel.retrieveArchivedPrivateJoinedThreadChannels().await())

                    try {
                        // We may not be able to retrieve private thread channels we have not joined.
                        addAll(channel.retrieveArchivedPrivateThreadChannels().await())
                    } catch (e: Exception) {
                        logger.warn("Failed to retrieve private thread channels: " + e.stackTraceToString())
                    }
                }.distinctBy { it.id }

                if (threadChannels.isNotEmpty()) {
                    val threadsDirectory = basePath.resolve("threads").createDirectory()

                    for (thread in threadChannels) {
                        archiveChannel(
                            channel = thread,
                            globalDataChannel = globalDataChannel,
                            basePath = threadsDirectory.resolve(thread.id).createDirectory(),
                        )
                    }
                }
            }
        }
    }
}

private fun forumTagData(it: ForumTag) = buildJsonObject {
    add("id", it.id)
    add("name", it.name)
    add("position", it.position)
    add("is_moderated", it.isModerated)
    it.emoji?.asReactionCode?.let { add("emoji", it) }
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
                        buildJsonObject {
                            add("name", channel.name)
                            add("type", channel.type.name)
                            add("instant_created", DateTimeFormatter.ISO_INSTANT.format(channel.timeCreated))

                            if (channel is IAgeRestrictedChannel) {
                                add("is_nsfw", channel.isNSFW)
                            }

                            if (channel is ISlowmodeChannel) {
                                add("slowmode", channel.slowmode)
                            }

                            if (channel is IPositionableChannel) {
                                add("position", channel.position)
                            }

                            if (channel is IPostContainer) {
                                channel.topic?.let { add("post_topic", it) }

                                add("post_tags", channel.availableTags.mapToJsonArray(::forumTagData))
                                channel.defaultReaction?.let { add("post_default_reaction", it.asReactionCode) }
                                add("post_is_tag_required", channel.isTagRequired)
                                add("post_default_sort_order", channel.defaultSortOrder.name)
                            }
                        }
                    }
                }
            }

            is ArchiveGlobalData.ReferencedRole -> {
                roleObjects.computeIfAbsent(data.id) { id ->
                    guild.getRoleById(id)?.let { role ->
                        buildJsonObject {
                            add("name", role.name)
                            add("position", role.position)
                            add("is_public_role", role.isPublicRole)
                            add("color", role.colorRaw)
                            add("is_hoisted", role.isHoisted)
                            add("is_managed", role.isManaged)
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

                    buildJsonObject {
                        add("username", user.name)

                        val globalName = user.globalName
                        if (globalName != null) {
                            add("global_name", globalName)
                        }

                        if (nickname != null) {
                            add("nickname", nickname)
                        }
                    }
                }
            }
        }
    }

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

private suspend fun archiveChannels(
    guild: Guild,
    archiveBasePath: Path,
    channelIds: Set<String>,
) {
    withChannel<ArchiveGlobalData>(
        capacity = 100,
        send = { globalDataChannel ->
            val channelsDir = archiveBasePath.resolve("channels")
            channelsDir.createDirectory()

            coroutineScope {
                for (channelId in channelIds) {
                    val channel = guild.getTextChannelById(channelId)

                    requireNotNull(channel) {
                        "Invalid channel id: $channelId"
                    }

                    launch {
                        val outDir = channelsDir.resolve(channelId)
                        outDir.createDirectory()

                        archiveChannel(
                            channel = channel,
                            globalDataChannel = globalDataChannel,
                            basePath = outDir,
                        )
                    }
                }
            }
        },
        receive = { globalDataChannel ->
            receiveGlobalData(
                dataChannel = globalDataChannel,
                guild = guild,
                outPath = archiveBasePath.resolve("global_data.json"),
            )
        }
    )
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

        withContext(Dispatchers.IO) {
            workDir.createDirectory()

            zipFileSystemProvider().newFileSystem(archivePath, ZIP_FILE_SYSTEM_CREATE_OPTIONS).use { zipFs ->
                val archiveBasePath = zipFs.getPath("archive").createDirectory()

                archiveChannels(
                    guild = guild,
                    archiveBasePath = archiveBasePath,
                    channelIds = channelIds,
                )
            }
        }

        check(archivePath.isRegularFile())

        return archivePath
    }

    override val archiveExtension: String
        get() = "zip"
}
