package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.randomcat.agorabot.util.updateAndMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        return OffsetDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(decoder.decodeString()))
    }

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        return encoder.encodeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value))
    }
}

@Serializable
@SerialName("DigestMessage")
private data class DigestMessageDto(
    val senderUsername: String,
    val senderNickname: String?,
    val id: String,
    val channelName: String? = null, // Default is for backwards compat
    val content: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("date")
    val messageDate: OffsetDateTime,
    val attachmentUrls: List<String> = emptyList(), // Default is for compatibility with versions before this was added
) {
    companion object {
        fun fromMessage(message: DigestMessage) = DigestMessageDto(
            senderNickname = message.senderNickname,
            senderUsername = message.senderUsername,
            id = message.id,
            channelName = message.channelName,
            content = message.content,
            messageDate = message.messageDate.atOffset(ZoneOffset.UTC),
            attachmentUrls = message.attachmentUrls.toList(),
        )
    }

    fun toMessage() = DigestMessage(
        senderUsername = senderUsername,
        senderNickname = senderNickname,
        id = id,
        channelName = channelName,
        content = content,
        messageDate = messageDate.toInstant(),
        attachmentUrls = attachmentUrls.toImmutableList(),
    )
}

private class JsonDigest(
    private val storagePath: Path,
    private val backupDir: Path,
) : MutableDigest {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun Iterable<DigestMessageDto>.distinctById() = distinctBy { it.id }

        private fun readFromFile(storagePath: Path): List<DigestMessageDto> {
            if (Files.notExists(storagePath)) return emptyList()

            val contentString = Files.readString(storagePath, FILE_CHARSET)

            return Json.decodeFromString<List<DigestMessageDto>>(contentString).distinctById()
        }

        private fun writeToFile(storagePath: Path, messages: List<DigestMessageDto>) {
            withTempFile { tempFile ->
                Files.writeString(
                    tempFile,
                    Json.encodeToString<List<DigestMessageDto>>(messages),
                    FILE_CHARSET,
                )

                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private val rawMessages = AtomicReference(readFromFile(storagePath).toPersistentList())
    private val backupCounter = AtomicLong()

    fun schedulePersistenceOn(service: ConfigPersistService) {
        service.schedulePersistence({ rawMessages.get() }, { writeToFile(storagePath, it) })
    }

    override fun messages(): ImmutableList<DigestMessage> {
        return rawMessages.get().map { it.toMessage() }.toImmutableList()
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun add(newMessages: Iterable<DigestMessage>) {
        val newRaw = newMessages.map { DigestMessageDto.fromMessage(it) }

        rawMessages.getAndUpdate {
            it.addAll(newRaw).distinctById().toPersistentList()
        }
    }

    override fun addCounted(messages: Iterable<DigestMessage>): Int {
        val newRaw = messages.map { DigestMessageDto.fromMessage(it) }

        return rawMessages.updateAndMap { orig ->
            // This requires that orig already be distinct by id. add upholds this invariant when adding
            val result = orig.addAll(newRaw).distinctById().toPersistentList()
            result to (result.size - orig.size)
        }
    }

    override fun clear() {
        val oldValue = rawMessages.getAndSet(persistentListOf())

        Files.createDirectories(backupDir)

        // I refuse to believe that a Guild will run through all of the possible Long values for backups within a single
        // second. A Long will suffice here.
        val backupPath = backupDir.resolve(
            DateTimeFormatter
                .ISO_LOCAL_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
                    + "-" +
                    "%020d".format(backupCounter.getAndUpdate { old ->
                        if (old == Long.MAX_VALUE) 0 else (old + 1) // Don't allow negative numbers
                    })
        )

        writeToFile(backupPath, oldValue)
    }
}

class JsonGuildDigestMap(
    private val storageDirectory: Path,
    private val persistenceService: ConfigPersistService,
) : GuildMutableDigestMap {
    init {
        Files.createDirectories(storageDirectory)
    }

    private val backupDirectory
        get() = storageDirectory.resolve("cleared")

    private val map = AtomicLoadOnceMap<String /* GuildId */, JsonDigest>()

    override fun digestForGuild(guildId: String): MutableDigest {
        return map.getOrPut(guildId) {
            JsonDigest(
                storagePath = storageDirectory.resolve(guildId),
                backupDir = backupDirectory.resolve(guildId),
            ).also { it.schedulePersistenceOn(persistenceService) }
        }
    }
}
