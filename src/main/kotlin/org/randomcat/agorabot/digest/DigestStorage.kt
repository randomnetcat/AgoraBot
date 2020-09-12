package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.util.updateAndMap
import org.randomcat.agorabot.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

        private fun readFromFile(storagePath: Path): List<DigestMessageDto> {
            if (Files.notExists(storagePath)) return emptyList()

            val contentString = Files.readString(storagePath, FILE_CHARSET)

            return Json.decodeFromString<List<DigestMessageDto>>(contentString)
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

    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(object : Runnable {
            private var lastList: List<DigestMessageDto>? = null

            override fun run() {
                val newList: List<DigestMessageDto> = rawMessages.get()

                if (newList != lastList) {
                    writeToFile(storagePath, newList)
                }
            }
        }, 0, 5, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread {
            writeToFile(storagePath, rawMessages.get())
        })
    }

    override fun messages(): ImmutableList<DigestMessage> {
        return rawMessages.get().map { it.toMessage() }.toImmutableList()
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun add(newMessages: Iterable<DigestMessage>) {
        val newRaw = newMessages.map { DigestMessageDto.fromMessage(it) }

        rawMessages.getAndUpdate {
            it.addAll(newRaw).distinctBy { msg -> msg.id }.toPersistentList()
        }
    }

    override fun addCounted(messages: Iterable<DigestMessage>): Int {
        val newRaw = messages.map { DigestMessageDto.fromMessage(it) }

        return rawMessages.updateAndMap { orig ->
            // This requires that orig already be distinct by id. add upholds this invariant when adding
            val result = orig.addAll(newRaw).distinctBy { msg -> msg.id }.toPersistentList()
            result to (result.size - orig.size)
        }
    }

    override fun clear() {
        rawMessages.set(persistentListOf())
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
