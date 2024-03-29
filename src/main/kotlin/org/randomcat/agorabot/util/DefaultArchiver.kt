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
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.channel.attribute.*
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTag
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
import net.dv8tion.jda.api.interactions.components.Component
import org.randomcat.agorabot.commands.DiscordArchiver
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.Writer
import java.math.BigInteger
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
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
    send: suspend CoroutineScope.(SendChannel<T>) -> Unit,
    receive: suspend CoroutineScope.(ReceiveChannel<T>) -> Unit,
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
    channelId: String,
    attachmentChannel: ReceiveChannel<PendingAttachmentDownload>,
    attachmentsDir: Path,
) {
    withContext(Dispatchers.IO) {
        for (pendingDownload in attachmentChannel) {
            launch {
                val number = pendingDownload.attachmentNumber

                val attachment = pendingDownload.attachment
                val originalFilename = attachment.fileName

                // Some discord attachments have extremely long filenames. So, we replace everything but the extension
                // with "content" and then, just to be sure, only take the first 20 characters of the adjusted name
                // are used.
                val adjustedFileName = if (originalFilename.contains(".")) {
                    "content.${originalFilename.substringAfterLast('.')}".take(20)
                } else {
                    "content"
                }

                val attachmentDir = attachmentsDir.resolve("attachment-$number")
                attachmentDir.createDirectory()

                try {
                    val outPath = attachmentDir.resolve(adjustedFileName).normalize()
                    check(outPath.startsWith(attachmentDir))

                    logger.info("Downloading channel $channelId attachment $number; original name: $originalFilename; stored name: $adjustedFileName; url: ${attachment.url}")

                    attachmentDir.resolve("metadata.json").outputStream(StandardOpenOption.CREATE_NEW).use { out ->
                        Json.createGenerator(out).use { json ->
                            json.writeStartObject()

                            json.write("id", attachment.id)
                            json.write("url", attachment.url)
                            json.write("original_filename", originalFilename)
                            json.write("size", attachment.size)
                            json.write("is_spoiler", attachment.isSpoiler)
                            json.write("is_ephemeral", attachment.isEphemeral)
                            json.write("is_image", attachment.isImage)
                            json.write("is_video", attachment.isVideo)
                            attachment.description?.let { json.write("description", it) }
                            attachment.contentType?.let { json.write("content_type", it) }
                            attachment.width.takeIf { it != -1 }?.let { json.write("width", it) }
                            attachment.height.takeIf { it != -1 }?.let { json.write("height", it) }
                            attachment.duration.takeIf { it != 0.0 }?.let { json.write("duration", it) }

                            json.writeEnd()
                        }
                    }

                    outPath.outputStream(StandardOpenOption.CREATE_NEW).use { outStream ->
                        writeAttachmentContentTo(attachment, outStream)
                    }

                    logger.info("Finished downloading channel $channelId attachment $number")
                } catch (e: Exception) {
                    logger.error(
                        "Error downloading channel $channelId attachment $number; name: $originalFilename; stored name: $adjustedFileName; url: ${attachment.url}",
                        e,
                    )

                    attachmentDir.resolve("ERROR.txt").writeText(e.stackTraceToString())
                }
            }
        }
    }
}

private suspend fun receiveReactions(
    channelId: String,
    reactionChannel: ReceiveChannel<PendingReactionInfo>,
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    reactionOut: Writer,
) {
    Json.createGenerator(reactionOut.nonClosingView()).use { generator ->
        generator.writeStartObject()
        generator.writeKey("messages")
        generator.writeStartObject()

        coroutineScope {
            withChannel<Pair<String, JsonObject>>(
                capacity = 100,
                send = { resultChannel ->
                    coroutineScope {
                        for (reactionInfo in reactionChannel) {
                            launch {
                                val reactions = reactionInfo.reactions
                                val reactionUsers =
                                    reactions.map { it.retrieveUsers().submit().asDeferred() }.awaitAll()

                                launch {
                                    resultChannel.send(reactionInfo.messageId to buildJsonObject {
                                        add("reactions", buildJsonArray {
                                            for ((emoji, users) in (reactions zip reactionUsers)) {
                                                add(buildJsonObject {
                                                    add("emoji", emoji.emoji.asReactionCode)
                                                    add("user_count", users.size)
                                                    add("users", users.mapToJsonArray { it.id })
                                                })
                                            }
                                        })
                                    })
                                }

                                launch {
                                    for (user in reactionUsers.asSequence().flatten()) {
                                        globalDataChannel.send(ArchiveGlobalData.ReferencedUser(id = user.id))
                                    }
                                }
                            }
                        }
                    }
                },
                receive = { resultChannel ->
                    withContext(Dispatchers.IO) {
                        var count = 0L

                        for (result in resultChannel) {
                            ++count

                            if (count % 100L == 0L) {
                                logger.info("Receiving reactions for channel $channelId: writing message $count")
                            }

                            generator.write(result.first, result.second)
                        }
                    }
                },
            )
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

private fun componentJsonData(component: Component): JsonObject {
    return buildJsonObject {
        add("type", component.type.name)
        add("data", component.toData().toString())
    }
}

private fun embedJsonData(embed: MessageEmbed): JsonObject {
    return buildJsonObject {
        add("type", embed.type.name)
        add("color", embed.colorRaw)

        embed.url?.let { add("url", it) }
        embed.title?.let { add("title", it) }
        embed.description?.let { add("description", it) }
        embed.timestamp?.let { add("timestamp_instant", DateTimeFormatter.ISO_INSTANT.format(it)) }

        embed.author?.let { author ->
            add("author", buildJsonObject {
                author.name?.let { add("name", it) }
                author.url?.let { add("url", it) }
                author.iconUrl?.let { add("icon_url", it) }
            })
        }

        embed.image?.let { image ->
            add("image", buildJsonObject {
                image.url?.let { add("url", it) }
                add("width", image.width)
                add("height", image.height)
            })
        }

        embed.videoInfo?.let { video ->
            add("video_info", buildJsonObject {
                video.url?.let { add("url", it) }
                add("width", video.width)
                add("height", video.height)
            })
        }

        embed.thumbnail?.let { thumbnail ->
            add("thumbnail", buildJsonObject {
                thumbnail.url?.let { add("url", it) }
                add("width", thumbnail.width)
                add("height", thumbnail.height)
            })
        }

        embed.footer?.let { footer ->
            add("footer", buildJsonObject {
                footer.text?.let { add("text", it) }
                footer.iconUrl?.let { add("icon_url", it) }
            })
        }

        add("fields", embed.fields.mapToJsonArray { field ->
            buildJsonObject {
                add("name", field.name)
                add("value", field.value)
                add("is_inline", field.isInline)
            }
        })

        embed.siteProvider?.let { siteProvider ->
            add("site_provider", buildJsonObject {
                siteProvider.name?.let { add("name", it) }
                siteProvider.url?.let { add("url", it) }
            })
        }

        add("data", embed.toData().toString())
    }
}

private fun JsonGenerator.writeMessage(message: Message, attachmentNumbers: List<BigInteger>) {
    val messageObject = buildJsonObject {
        add("type", message.type.name)
        add("author_id", message.author.id)
        add("raw_text", message.contentRaw)
        if (!message.type.isSystem) add("display_text", message.contentDisplay)

        if (message.isWebhookMessage) {
            val author = message.author

            add("author_webhook", buildJsonObject {
                add("id", author.id)
                add("name", author.name)
                author.globalName?.let { add("global_name", it) }
                author.avatarUrl?.let { add("avatar_url", it) }
            })
        }

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

        add("components", message.components.mapToJsonArray(::componentJsonData))
        add("embeds", message.embeds.mapToJsonArray(::embedJsonData))
    }

    write(
        message.id,
        messageObject,
    )
}

private suspend fun receiveMessages(
    channelId: String,
    messageChannel: ReceiveChannel<Message>,
    attachmentChannel: SendChannel<PendingAttachmentDownload>,
    reactionChannel: SendChannel<PendingReactionInfo>,
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    textOut: Writer,
    jsonOut: Writer,
) {
    var currentAttachmentNumber = BigInteger.ZERO
    var messageCount = 0L
    var messageWithReactionCount = 0L

    Json.createGenerator(jsonOut.nonClosingView()).use { jsonGenerator ->
        jsonGenerator.writeStartMessages()

        for (message in messageChannel) {
            ++messageCount
            if (messageCount % 100L == 0L) {
                logger.info("Archiving channel $channelId: on message $messageCount")
            }

            val attachmentNumbers = message.attachments.map {
                val number = ++currentAttachmentNumber
                attachmentChannel.send(PendingAttachmentDownload(it, number))

                number
            }

            if (attachmentNumbers.isNotEmpty()) {
                logger.info("Channel $channelId message ${message.id} has attachments: ${attachmentNumbers.joinToString()}")
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
                ++messageWithReactionCount

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

    logger.info("Finished processing messages for channel $channelId: $messageCount messages, of which $messageWithReactionCount had reactions to be processed")
}

private sealed class ArchiveGlobalData {
    data class ReferencedUser(val id: String) : ArchiveGlobalData()
    data class ReferencedRole(val id: String) : ArchiveGlobalData()
    data class ReferencedChannel(val id: String) : ArchiveGlobalData()

    data class StartTime(val startInstant: Instant) : ArchiveGlobalData()
    data class EndTime(val endInstant: Instant) : ArchiveGlobalData()
}

private suspend fun archiveChannel(
    channel: GuildChannel,
    globalDataChannel: SendChannel<ArchiveGlobalData>,
    basePath: Path,
) {
    val channelId = channel.id

    logger.info("Beginning archive of channel $channelId")

    globalDataChannel.send(ArchiveGlobalData.ReferencedChannel(channelId))

    coroutineScope {
        if (channel is MessageChannel) {
            launch {
                withChannel<PendingAttachmentDownload>(
                    capacity = 100,
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
                                                        withContext(Dispatchers.IO) {
                                                            try {
                                                                channel.sendForwardHistoryTo(messageChannel)
                                                            } catch (e: Exception) {
                                                                val errorPath = basePath.resolve("ERROR_messages.txt")
                                                                errorPath.writeText(
                                                                    e.stackTraceToString(),
                                                                    options = arrayOf(StandardOpenOption.CREATE_NEW)
                                                                )

                                                                // Resolve ambiguity on getId
                                                                val channelSnowflake: ISnowflake = channel
                                                                logger.error(
                                                                    "Failed to get messages when archiving channel ${channelSnowflake.id}",
                                                                    e,
                                                                )
                                                            }
                                                        }
                                                    },
                                                    receive = { messageChannel ->
                                                        logger.info("Receiving messages for channel $channelId")

                                                        receiveMessages(
                                                            channelId = channelId,
                                                            messageChannel = messageChannel,
                                                            attachmentChannel = attachmentChannel,
                                                            reactionChannel = reactionChannel,
                                                            globalDataChannel = globalDataChannel,
                                                            textOut = textOut,
                                                            jsonOut = jsonOut,
                                                        )

                                                        attachmentChannel.close()
                                                        reactionChannel.close()

                                                        // Do NOT close globalDataChannel, since it's an argument

                                                        logger.info("Finished receiving messages for channel $channelId")
                                                    }
                                                )
                                            }
                                    }
                            },
                            receive = { reactionChannel ->
                                val reactionsPath = basePath.resolve("reactions.json")

                                reactionsPath.bufferedWriter(options = arrayOf(StandardOpenOption.CREATE_NEW))
                                    .use { reactionOut ->
                                        logger.info("Receiving reactions for channel $channelId")

                                        receiveReactions(
                                            channelId = channelId,
                                            reactionChannel = reactionChannel,
                                            globalDataChannel = globalDataChannel,
                                            reactionOut = reactionOut,
                                        )

                                        logger.info("Finished receiving reactions for channel $channelId")
                                    }
                            },
                        )
                    },
                    receive = { attachmentChannel ->
                        val attachmentsDir = basePath.resolve("attachments")
                        attachmentsDir.createDirectory()

                        logger.info("Receiving attachments for channel $channelId")

                        receivePendingDownloads(
                            channelId = channelId,
                            attachmentChannel = attachmentChannel,
                            attachmentsDir = attachmentsDir,
                        )

                        logger.info("Finished receiving attachments for channel $channelId")
                    }
                )
            }
        }

        if (channel is IThreadContainer) {
            launch {
                val threadChannels = buildList<ThreadChannel> {
                    addAll(channel.threadChannels)

                    try {
                        addAll(channel.retrieveArchivedPublicThreadChannels().await())
                    } catch (e: Exception) {
                        logger.warn("Failed to retrieve archived public thread channels: " + e.stackTraceToString())
                    }

                    try {
                        addAll(channel.retrieveArchivedPrivateJoinedThreadChannels().await())
                    } catch (e: Exception) {
                        logger.warn("Failed to retrieve archived joined private thread channels: " + e.stackTraceToString())
                    }

                    try {
                        addAll(channel.retrieveArchivedPrivateThreadChannels().await())
                    } catch (e: Exception) {
                        logger.warn("Failed to retrieve private thread channels: " + e.stackTraceToString())
                    }
                }.distinctBy { it.id }

                logger.info("Archiving ${threadChannels.size} threads of channel $channelId")

                coroutineScope {
                    if (threadChannels.isNotEmpty()) {
                        val threadsDirectory = basePath.resolve("threads").createDirectory()

                        for (thread in threadChannels) {
                            launch {
                                logger.info("Archiving thread ${thread.id} of channel $channelId")

                                archiveChannel(
                                    channel = thread,
                                    globalDataChannel = globalDataChannel,
                                    basePath = threadsDirectory.resolve(thread.id).createDirectory(),
                                )
                            }
                        }
                    }
                }

                logger.info("Done archiving threads of channel $channelId")
            }
        }
    }

    logger.info("Finished archive of channel $channelId")
}

private inline fun <R> usePathGenerator(
    path: Path,
    options: Array<OpenOption> = arrayOf(StandardOpenOption.CREATE_NEW),
    block: (JsonGenerator) -> R,
): R {
    return path.bufferedWriter(options = options).use { writer ->
        Json.createGenerator(writer).use { generator ->
            block(generator)
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

private fun channelMetadataJson(channel: GuildChannel) = buildJsonObject {
    add("id", channel.id)
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

    if (channel is StandardGuildMessageChannel) {
        channel.topic?.let { add("topic", it) }
    }

    if (channel is IPostContainer) {
        channel.topic?.let { add("post_topic", it) }

        add("post_tags", channel.availableTags.mapToJsonArray(::forumTagData))
        channel.defaultReaction?.let { add("post_default_reaction", it.asReactionCode) }
        add("post_is_tag_required", channel.isTagRequired)
        add("post_default_sort_order", channel.defaultSortOrder.name)
    }
}


private fun roleMetadataJson(role: Role) = buildJsonObject {
    add("id", role.id)
    add("name", role.name)
    add("position", role.position)
    add("is_public_role", role.isPublicRole)
    add("color", role.colorRaw)
    add("is_hoisted", role.isHoisted)
    add("is_managed", role.isManaged)
}

private suspend fun receiveGlobalDataWithGenerators(
    dataChannel: ReceiveChannel<ArchiveGlobalData>,
    guild: Guild,
    channelIds: Set<String>,
    metadataGenerator: JsonGenerator,
    usersGenerator: JsonGenerator,
    rolesGenerator: JsonGenerator,
    channelsGenerator: JsonGenerator,
) {
    val generatorKeys = listOf(usersGenerator to "users", rolesGenerator to "roles", channelsGenerator to "channels")

    withContext(Dispatchers.IO) {
        for ((generator, key) in generatorKeys) {
            generator.writeStartObject()
            generator.writeKey(key)
            generator.writeStartObject()
        }
    }

    val metadataObject = Json.createObjectBuilder()
    metadataObject.add("guild_id", guild.id)
    metadataObject.add("channel_ids", channelIds.sorted().mapToJsonArray { it })

    val seenUserIds = mutableSetOf<String>()
    val seenRoleIds = mutableSetOf<String>()
    val seenChannelIds = mutableSetOf<String>()

    for (data in dataChannel) {
        when (data) {
            is ArchiveGlobalData.ReferencedChannel -> {
                if (seenChannelIds.add(data.id)) {
                    val channel = guild.getGuildChannelById(data.id)

                    if (channel != null) {
                        channelsGenerator.write(data.id, channelMetadataJson(channel))
                    }
                }
            }

            is ArchiveGlobalData.ReferencedRole -> {
                if (seenRoleIds.add(data.id)) {
                    val role = guild.getRoleById(data.id)

                    if (role != null) {
                        rolesGenerator.write(data.id, roleMetadataJson(role))
                    }
                }
            }

            is ArchiveGlobalData.ReferencedUser -> {
                val id = data.id

                if (seenUserIds.add(id)) {
                    val member = runCatching { guild.retrieveMemberById(id).await() }.getOrNull()
                    val nickname = member?.nickname
                    val user =
                        member?.user
                            ?: runCatching { guild.jda.retrieveUserById(id).await() }.getOrNull()

                    val userJson = buildJsonObject {
                        add("id", id)
                        user?.name?.let { add("username", it) }
                        user?.globalName?.let { add("global_name", it) }
                        nickname?.let { add("nickname", it) }
                    }

                    usersGenerator.write(data.id, userJson)
                }
            }

            is ArchiveGlobalData.StartTime -> {
                metadataObject.add("start_instant", DateTimeFormatter.ISO_INSTANT.format(data.startInstant))
            }

            is ArchiveGlobalData.EndTime -> {
                metadataObject.add("end_instant", DateTimeFormatter.ISO_INSTANT.format(data.endInstant))
            }
        }
    }

    withContext(Dispatchers.IO) {
        metadataGenerator.writeStartObject()
        metadataGenerator.write("metadata", metadataObject.build())
        metadataGenerator.writeEnd()

        for ((generator, _) in generatorKeys) {
            generator.writeEnd()
            generator.writeEnd()
        }
    }
}

private fun writeGuildMetadata(globalDataDir: Path, guild: Guild) {
    usePathGenerator(globalDataDir.resolve("guild.json")) { guildGenerator ->
        guildGenerator.writeStartObject()
        guildGenerator.writeKey("guild")
        guildGenerator.writeStartObject()
        guildGenerator.write("id", guild.id)
        guildGenerator.write("name", guild.name)
        guild.description?.let { guildGenerator.write("description", it) }
        guild.iconUrl?.let { guildGenerator.write("icon_url", it) }
        guild.bannerUrl?.let { guildGenerator.write("banner_url", it) }
        guild.splashUrl?.let { guildGenerator.write("splash_url", it) }
        guildGenerator.writeEnd()
        guildGenerator.writeEnd()
    }
}

private suspend fun receiveGlobalData(
    dataChannel: ReceiveChannel<ArchiveGlobalData>,
    guild: Guild,
    channelIds: Set<String>,
    metadataPath: Path,
    globalDataDir: Path,
) {
    // Here, do all the opening and closing on the I/O dispatcher, so blocking reads/writes don't block on the default
    // dispatcher, but then return to the default dispatcher for the actual work, which should not be doing much
    // blocking I/O (and, in any case, receiveGlobalDataWithGenerators can switch dispatchers itself if it needs to).

    withContext(Dispatchers.IO) {
        usePathGenerator(metadataPath) { metadataGenerator ->
            usePathGenerator(globalDataDir.resolve("users.json")) { usersGenerator ->
                usePathGenerator(globalDataDir.resolve("roles.json")) { rolesGenerator ->
                    usePathGenerator(globalDataDir.resolve("channels.json")) { channelsGenerator ->
                        withContext(Dispatchers.Default) {
                            receiveGlobalDataWithGenerators(
                                dataChannel = dataChannel,
                                guild = guild,
                                channelIds = channelIds,
                                metadataGenerator = metadataGenerator,
                                usersGenerator = usersGenerator,
                                rolesGenerator = rolesGenerator,
                                channelsGenerator = channelsGenerator,
                            )
                        }
                    }
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
    val globalDataDir = archiveBasePath.resolve("global_data").createDirectory()

    writeGuildMetadata(
        guild = guild,
        globalDataDir = globalDataDir,
    )

    withChannel<ArchiveGlobalData>(
        capacity = 100,
        send = { globalDataChannel ->
            val channelsDir = archiveBasePath.resolve("channels")
            channelsDir.createDirectory()

            globalDataChannel.send(ArchiveGlobalData.StartTime(Instant.now()))

            coroutineScope {
                for (channelId in channelIds) {
                    val channel = guild.getGuildChannelById(channelId)

                    requireNotNull(channel) {
                        "Invalid channel id: $channelId"
                    }

                    launch {
                        val outDir = channelsDir.resolve(channelId).normalize()
                        check(outDir.startsWith(channelsDir))
                        outDir.createDirectory()

                        archiveChannel(
                            channel = channel,
                            globalDataChannel = globalDataChannel,
                            basePath = outDir,
                        )
                    }
                }
            }

            globalDataChannel.send(ArchiveGlobalData.EndTime(Instant.now()))
        },
        receive = { globalDataChannel ->
            receiveGlobalData(
                dataChannel = globalDataChannel,
                guild = guild,
                channelIds = channelIds,
                metadataPath = archiveBasePath.resolve("metadata.json"),
                globalDataDir = globalDataDir,
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
        logger.info("Beginning archive of guild ${guild.id} (${guild.name})")

        val archiveNumber = archiveCount.getAndUpdate { it + BigInteger.ONE }

        val workDir = storageDir.resolve("archive-$archiveNumber").createDirectory()
        val archivePath = workDir.resolve("archive").createDirectory()

        coroutineScope {
            archiveChannels(
                guild = guild,
                archiveBasePath = archivePath,
                channelIds = channelIds,
            )
        }

        logger.info("Finished archive of guild ${guild.id} (${guild.name})")

        return workDir
    }

    override val archiveExtension: String?
        get() = null
}
