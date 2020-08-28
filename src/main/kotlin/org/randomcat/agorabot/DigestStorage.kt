package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.commands.DigestMap
import org.randomcat.agorabot.commands.DigestMessage
import org.randomcat.agorabot.commands.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

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
) {
    companion object {
        fun fromMessage(message: DigestMessage) = DigestMessageDto(
            senderNickname = message.senderNickname,
            senderUsername = message.senderUsername,
            id = message.id,
            content = message.content,
            date = message.date,
        )
    }

    fun toMessage() = DigestMessage(
        senderUsername = senderUsername,
        senderNickname = senderNickname,
        id = id,
        content = content,
        date = date,
    )
}

private class JsonMessageDigest(
    private val storagePath: Path,
) : MessageDigest {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun readInitial(storagePath: Path): List<DigestMessageDto> {
            if (Files.notExists(storagePath)) return emptyList()

            val contentBytes = Files.readAllBytes(storagePath)
            val contentString = String(contentBytes, FILE_CHARSET)

            return Json.decodeFromString<List<DigestMessageDto>>(contentString)
        }
    }

    private var rawMessages: MutableList<DigestMessageDto> = readInitial(storagePath).toMutableList()

    private fun persist() {
        val string = Json.encodeToString<List<DigestMessageDto>>(rawMessages)
        val bytes = string.toByteArray(FILE_CHARSET)
        Files.write(storagePath, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
    }

    override fun messages(): ImmutableList<DigestMessage> {
        return rawMessages.map { it.toMessage() }.toImmutableList()
    }

    override fun add(message: Iterable<DigestMessage>) {
        rawMessages.addAll(message.map { DigestMessageDto.fromMessage(it) })
        rawMessages = rawMessages.distinctBy { it.id }.toMutableList()
        persist()
    }

    override fun clear() {
        rawMessages.clear()
        persist()
    }

    override fun render(): String {
        return messages().sortedBy { it.date }.joinToString("\n\n") {
            val nickname = it.senderNickname
            val includeNickname = (nickname != null) && (nickname != it.senderUsername)

            "MESSAGE ${it.id}\n" +
                    "FROM ${it.senderUsername}${if (includeNickname) " ($nickname)" else ""} " +
                    "ON ${DateTimeFormatter.ISO_LOCAL_DATE.format(it.date)} " +
                    "AT ${DateTimeFormatter.ISO_LOCAL_TIME.format(it.date)}:" +
                    "\n" +
                    it.content
        }
    }
}

class JsonDigestMap(
    private val storageDirectory: Path,
) : DigestMap {
    init {
        Files.createDirectories(storageDirectory)
    }

    override fun digestForGuild(guildId: String): MessageDigest {
        val jsonFile = storageDirectory.resolve(guildId)
        return JsonMessageDigest(jsonFile)
    }
}
