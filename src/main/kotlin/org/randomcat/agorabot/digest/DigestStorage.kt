package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
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
    val content: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val date: OffsetDateTime,
    val attachmentUrls: List<String> = emptyList(), // Default is for compatibility with versions before this was added
) {
    companion object {
        fun fromMessage(message: DigestMessage) = DigestMessageDto(
            senderNickname = message.senderNickname,
            senderUsername = message.senderUsername,
            id = message.id,
            content = message.content,
            date = message.date,
            attachmentUrls = message.attachmentUrls.toList(),
        )
    }

    fun toMessage() = DigestMessage(
        senderUsername = senderUsername,
        senderNickname = senderNickname,
        id = id,
        content = content,
        date = date,
        attachmentUrls = attachmentUrls.toImmutableList(),
    )
}

private class JsonDigest(
    private val storagePath: Path,
) : Digest {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun readInitial(storagePath: Path): List<DigestMessageDto> {
            if (Files.notExists(storagePath)) return emptyList()

            val contentString = Files.readString(storagePath, FILE_CHARSET)

            return Json.decodeFromString<List<DigestMessageDto>>(contentString)
        }
    }

    private var _rawUnlockedMessages: MutableList<DigestMessageDto> = readInitial(storagePath).toMutableList()

    private val lock = ReentrantReadWriteLock()

    private inline fun <R> read(block: (messages: List<DigestMessageDto>) -> R): R {
        return lock.read { block(_rawUnlockedMessages) }
    }

    private inline fun <R> write(block: (messages: MutableList<DigestMessageDto>) -> R): R {
        return lock.write {
            val result = block(_rawUnlockedMessages)
            persistUnlocked()
            result
        }
    }

    private inline fun replace(block: (messages: MutableList<DigestMessageDto>) -> MutableList<DigestMessageDto>) {
        lock.write {
            _rawUnlockedMessages = block(_rawUnlockedMessages)
            persistUnlocked()
        }
    }

    private fun persistUnlocked() {
        withTempFile { tempFile ->
            Files.writeString(
                tempFile,
                Json.encodeToString<List<DigestMessageDto>>(_rawUnlockedMessages),
                FILE_CHARSET,
            )

            Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun rawMessages(): ImmutableList<DigestMessageDto> {
        return read { it.toImmutableList() } // Don't permit a reference to the unsynchronized list to escape
    }

    override fun messages(): ImmutableList<DigestMessage> {
        return rawMessages().map { it.toMessage() }.toImmutableList()
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun add(newMessages: Iterable<DigestMessage>) {
        replace { rawMessages ->
            (rawMessages + newMessages.map { msg -> DigestMessageDto.fromMessage(msg) })
                .distinctBy { msg -> msg.id }
                .toMutableList()
        }
    }

    override fun addCounted(messages: Iterable<DigestMessage>): Int {
        write { _ ->
            // Can't use parameter because add replaces the reference.
            val oldSize = _rawUnlockedMessages.size
            add(messages)
            val newSize = _rawUnlockedMessages.size

            return newSize - oldSize
        }
    }

    override fun clear() {
        // Instead of replacing with empty list, clear and maintain list's capacity
        write { it.clear() }
    }
}

class JsonGuildDigestMap(
    private val storageDirectory: Path,
) : GuildDigestMap {
    init {
        Files.createDirectories(storageDirectory)
    }

    private class LoadOnceDigest(private val path: Path) {
        // lazy will ensure that only a single JsonDigest is created
        val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { JsonDigest(path) }
    }

    private val map = AtomicReference<PersistentMap<String, LoadOnceDigest>>(persistentMapOf())

    override fun digestForGuild(guildId: String): Digest {
        run {
            val origMap = map.get()

            val existingAnswer = origMap[guildId]
            if (existingAnswer != null) return existingAnswer.value
        }

        return map.updateAndGet { origMap ->
            // Possible race condition - another thread could already have added it.
            // This means we have to check if it's already in the map.
            if (origMap.containsKey(guildId))
                origMap
            else
            // Multiple LoadOnceDigests might be created as the threads compete, but only one will be returned
            // to the outside world. Then, once that one instance is returned, it will thread-safely create
            // a single JsonDigest.
                origMap.put(guildId, LoadOnceDigest(storageDirectory.resolve(guildId)))
        }.getValue(guildId).value
    }
}
