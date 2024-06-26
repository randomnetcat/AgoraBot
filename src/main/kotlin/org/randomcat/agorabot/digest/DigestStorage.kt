package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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
    persistService: ConfigPersistService,
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

    private val lock = ReentrantReadWriteLock()
    private var isClosed = false

    private var rawMessages = readFromFile(storagePath).toPersistentList()
    private var backupCounter: ULong = 0.toULong()

    private val persistHandle =
        persistService.schedulePersistence({
            lock.read {
                ensureOpen()
                rawMessages
            }
        }, { writeToFile(storagePath, it) })

    private fun ensureOpen() {
        check(!isClosed)
    }

    override fun messages(): ImmutableList<DigestMessage> {
        lock.read {
            ensureOpen()
            return rawMessages.map { it.toMessage() }.toImmutableList()
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun add(newMessages: Iterable<DigestMessage>) {
        lock.write {
            ensureOpen()

            rawMessages = rawMessages
                .addAll(newMessages.map { DigestMessageDto.fromMessage(it) })
                .distinctById()
                .toPersistentList()
        }
    }

    override fun addCounted(messages: Iterable<DigestMessage>): Int {
        lock.write {
            ensureOpen()

            val newRaw = messages.map { DigestMessageDto.fromMessage(it) }

            val oldSize = rawMessages.size
            rawMessages = rawMessages.addAll(newRaw).distinctById().toPersistentList()

            return rawMessages.size - oldSize
        }
    }

    override fun clear() {
        lock.write {
            ensureOpen()

            Files.createDirectories(backupDir)

            // I refuse to believe that a Guild will run through all of the possible Long values for backups within a single
            // second. A ULong will suffice here.
            val backupPath = backupDir.resolve(
                DateTimeFormatter
                    .ISO_LOCAL_DATE_TIME
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now())
                        + "-" +
                        "%020d".format(++backupCounter)
            )

            writeToFile(backupPath, rawMessages)

            rawMessages = persistentListOf()
        }
    }

    fun close() {
        lock.write {
            if (isClosed) return
            isClosed = true

            persistHandle.stopPersistence()
        }
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
                persistService = persistenceService,
            )
        }
    }

    fun close() {
        map.closeAndTake().values.forEach { it.close() }
    }
}
