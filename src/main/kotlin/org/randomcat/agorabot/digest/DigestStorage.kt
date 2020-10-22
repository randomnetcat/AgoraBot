package org.randomcat.agorabot.digest

import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.util.updateAndMap
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    val date: OffsetDateTime,
    val attachmentUrls: List<String> = emptyList(), // Default is for compatibility with versions before this was added
) {
    companion object {
        fun fromMessage(message: DigestMessage) = DigestMessageDto(
            senderNickname = message.senderNickname,
            senderUsername = message.senderUsername,
            id = message.id,
            channelName = message.channelName,
            content = message.content,
            date = message.date,
            attachmentUrls = message.attachmentUrls.toList(),
        )
    }

    fun toMessage() = DigestMessage(
        senderUsername = senderUsername,
        senderNickname = senderNickname,
        id = id,
        channelName = channelName,
        content = content,
        date = date,
        attachmentUrls = attachmentUrls.toImmutableList(),
    )
}

private class JsonDigest(
    private val storagePath: Path,
    private val backupDir: Path,
) : Digest {
    companion object {
        private val FILE_CHARSET = Charsets.UTF_8

        private fun Iterable<DigestMessageDto>.distinctById() = distinctBy { it.id }

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
    private val backupCounter = AtomicLong()

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
        val oldValue = rawMessages.get()
        rawMessages.set(persistentListOf())

        Files.createDirectories(backupDir)

        // I refuse to believe that a Guild will run through all of the possible Long values for backups within a single
        // second. A Long will suffice here.
        val backupPath = backupDir.resolve(
            DateTimeFormatter
                .ofPattern("YYYY-MM-dd-HH-mm-ss")
                .withZone(ZoneId.systemDefault())
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
) : GuildDigestMap {
    init {
        Files.createDirectories(storageDirectory)
    }

    private val backupDirectory
        get() = storageDirectory.resolve("cleared")

    private inner class LoadOnceDigest(init: () -> Digest) {
        // lazy will ensure that only a single JsonDigest is created
        val value by lazy(LazyThreadSafetyMode.SYNCHRONIZED, init)
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
                origMap.put(guildId, LoadOnceDigest {
                    JsonDigest(
                        storagePath = storageDirectory.resolve(guildId),
                        backupDir = backupDirectory.resolve(guildId),
                    ).also { it.schedulePersistenceOn(persistenceService) }
                })
        }.getValue(guildId).value
    }
}
