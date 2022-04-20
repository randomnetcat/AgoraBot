package org.randomcat.agorabot.community_message.impl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.randomcat.agorabot.community_message.*
import org.randomcat.agorabot.util.AtomicLoadOnceMap
import org.randomcat.agorabot.util.zipFileSystemProvider
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.*

@Serializable
private sealed class RevisionMetadataDto {
    @Serializable
    @SerialName("v0")
    data class Version0(
        val author: String,
        val revisionInstantString: String,
    ) : RevisionMetadataDto()

    companion object {
        fun from(metadata: CommunityMessageRevisionMetadata): RevisionMetadataDto {
            return Version0(
                author = metadata.author,
                revisionInstantString = metadata.revisionTime.toString(),
            )
        }
    }
}

@Serializable
private sealed class MessageMetadataDto {
    @Serializable
    @SerialName("v0")
    data class Version0(
        val createdBy: String,
        val creationTimeString: String,
        val channelId: String,
        val messageId: String,
        val maxRevision: Long?,
        val group: String? = null,
    ) : MessageMetadataDto() {
        override fun build(): CommunityMessageMetadata {
            return CommunityMessageMetadata(
                createdBy = createdBy,
                creationTime = Instant.parse(creationTimeString),
                channelId = channelId,
                messageId = messageId,
                maxRevision = maxRevision?.let { CommunityMessageRevisionNumber(it) },
                group = group,
            )
        }
    }

    companion object {
        fun from(metadata: CommunityMessageMetadata): MessageMetadataDto {
            return Version0(
                createdBy = metadata.createdBy,
                creationTimeString = metadata.creationTime.toString(),
                channelId = metadata.channelId,
                messageId = metadata.messageId,
                maxRevision = metadata.maxRevision?.value,
                group = metadata.group,
            )
        }
    }

    abstract fun build(): CommunityMessageMetadata
}

@Serializable
private sealed class GlobalMetadataDto {
    @Serializable
    @SerialName("v0")
    data class Version0(
        val rawNamesToInternalNames: Map<String, String>,
        val oldNames: List<Pair<String, String>> = emptyList(),
    ) : GlobalMetadataDto()
}

private val logger = LoggerFactory.getLogger("JsonCommunityMessageStorage")

class JsonCommunityMessageGuildStorage(
    private val storageFS: FileSystem,
) : CommunityMessageGuildStorage {
    @JvmInline
    private value class InternalName(val value: String)

    private val dataLock = ReentrantReadWriteLock()
    private var isClosed = false

    private fun messageBasePath(internalName: InternalName): Path {
        return storageFS.getPath("messages", internalName.value)
    }

    private fun messageMetadataPath(internalName: InternalName): Path {
        return messageBasePath(internalName).resolve("metadata.json")
    }

    private fun revisionListPath(internalName: InternalName): Path {
        return messageBasePath(internalName).resolve("revision_metadata.json")
    }

    private fun revisionsDirPath(internalName: InternalName): Path {
        return messageBasePath(internalName).resolve("revisions")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeMetadata(internalName: InternalName, metadataDto: MessageMetadataDto) {
        dataLock.write {
            messageMetadataPath(internalName).outputStream().use {
                Json.encodeToStream<MessageMetadataDto>(metadataDto, it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readMetadata(internalName: InternalName): MessageMetadataDto {
        return dataLock.read {
            messageMetadataPath(internalName).inputStream().use {
                Json.decodeFromStream<MessageMetadataDto>(it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeRevisionList(internalName: InternalName, revisions: Map<Long, RevisionMetadataDto>) {
        dataLock.write {
            revisionListPath(internalName).outputStream().use {
                Json.encodeToStream<Map<Long, RevisionMetadataDto>>(revisions, it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readRevisionList(internalName: InternalName): Map<Long, RevisionMetadataDto> {
        return dataLock.read {
            revisionListPath(internalName).inputStream().use {
                Json.decodeFromStream<Map<Long, RevisionMetadataDto>>(it)
            }
        }
    }

    private val globalMetadataPath = storageFS.getPath("global_metadata.json")

    @OptIn(ExperimentalSerializationApi::class)
    private fun readGlobalMetadata(): GlobalMetadataDto? {
        return dataLock.read {
            if (!globalMetadataPath.exists()) return null

            globalMetadataPath.inputStream().use {
                Json.decodeFromStream<GlobalMetadataDto>(it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeGlobalMetadata(metadata: GlobalMetadataDto) {
        return dataLock.read {
            globalMetadataPath.outputStream().use {
                Json.encodeToStream<GlobalMetadataDto>(metadata, it)
            }
        }
    }

    private fun lookupInternalName(rawName: String): InternalName? {
        return when (val metadata = readGlobalMetadata()) {
            null -> null
            is GlobalMetadataDto.Version0 -> metadata.rawNamesToInternalNames[rawName]?.let { InternalName(it) }
        }
    }

    private fun uniqueInternalName(): InternalName {
        return when (val metadata = readGlobalMetadata()) {
            null -> InternalName(UUID.randomUUID().toString())

            is GlobalMetadataDto.Version0 -> {
                var currentName = UUID.randomUUID().toString()

                while (metadata.rawNamesToInternalNames.containsValue(currentName)) {
                    currentName = UUID.randomUUID().toString()
                }

                InternalName(currentName)
            }
        }
    }

    private fun addInternalName(rawName: String, internalName: InternalName) {
        dataLock.write {
            return when (val metadata =
                readGlobalMetadata() ?: GlobalMetadataDto.Version0(rawNamesToInternalNames = emptyMap(),
                    oldNames = emptyList())) {
                is GlobalMetadataDto.Version0 -> {
                    writeGlobalMetadata(
                        metadata.copy(
                            rawNamesToInternalNames = metadata.rawNamesToInternalNames.toMutableMap().apply {
                                check(!containsKey(rawName))
                                put(rawName, internalName.value)
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun createMessage(name: String, metadata: CommunityMessageMetadata): Boolean {
        val metadataDto = MessageMetadataDto.from(metadata)

        dataLock.write {
            check(!isClosed)

            if (lookupInternalName(name) != null) return false

            val newInternalName = uniqueInternalName()
            val basePath = messageBasePath(newInternalName)

            if (Files.exists(basePath)) return false
            Files.createDirectories(basePath)

            writeMetadata(
                internalName = newInternalName,
                metadataDto = metadataDto,
            )

            writeRevisionList(
                internalName = newInternalName,
                revisions = emptyMap(),
            )

            addInternalName(name, newInternalName)

            return true
        }
    }

    override fun messageMetadata(name: String): CommunityMessageMetadata? {
        dataLock.read {
            check(!isClosed)
            val internalName = lookupInternalName(name) ?: return null
            return runCatching { readMetadata(internalName) }.getOrElse { return null }.build()
        }
    }

    override fun createRevision(
        name: String,
        metadata: CommunityMessageRevisionMetadata,
        content: String,
    ): CommunityMessageRevisionNumber? {
        dataLock.write {
            try {
                val internalName = lookupInternalName(name) ?: return null

                val messageMetadata = readMetadata(internalName).build()

                val newNumber = messageMetadata.maxRevision?.value?.let { it + 1 } ?: 0
                check(newNumber >= 0)

                // First, increment the revision number. If this fails, nothing bad happens, since no other state
                // has been updated.

                writeMetadata(
                    internalName = internalName,
                    metadataDto = MessageMetadataDto.from(
                        messageMetadata.copy(maxRevision = CommunityMessageRevisionNumber(newNumber)),
                    ),
                )

                // Next, write out the revision. If this fails, the revision hasn't been recorded yet, so the list
                // is still consistent.

                val currentDir =
                    revisionsDirPath(internalName).createDirectories().resolve("revision_$newNumber").createDirectory()
                currentDir.resolve("content.txt").writeText(content, Charsets.UTF_8, StandardOpenOption.CREATE_NEW)

                // Finally, record the revision in the list. If this fails, a revision has been written but not recorded,
                // but the list remains self-consistent.

                writeRevisionList(
                    internalName = internalName,
                    revisions = readRevisionList(internalName = internalName).toMutableMap().apply {
                        put(newNumber, RevisionMetadataDto.from(metadata))
                    },
                )

                return CommunityMessageRevisionNumber(newNumber)
            } catch (e: IOException) {
                logger.error("IOException while adding revision to message $name", e)
                return null
            }
        }
    }

    override fun messageNames(): Set<String> {
        return when (val metadata = readGlobalMetadata()) {
            null -> emptySet()
            is GlobalMetadataDto.Version0 -> metadata.rawNamesToInternalNames.keys.toSet()
        }
    }

    override fun removeMessage(name: String): Boolean {
        dataLock.write {
            when (val metadata = readGlobalMetadata()) {
                null -> return false
                is GlobalMetadataDto.Version0 -> {
                    if (!metadata.rawNamesToInternalNames.containsKey(name)) return false

                    writeGlobalMetadata(
                        metadata.copy(
                            rawNamesToInternalNames = metadata.rawNamesToInternalNames.toMutableMap().apply {
                                remove(name)
                            },
                            oldNames = metadata.oldNames.toMutableList().apply {
                                add(name to metadata.rawNamesToInternalNames.getValue(name))
                            },
                        )
                    )

                    return true
                }
            }
        }
    }

    fun close() {
        dataLock.write {
            if (isClosed) return
            isClosed = true

            storageFS.close()
        }
    }
}

private val ZIP_FS_OPTIONS = mapOf("create" to true)

class JsonCommunityMessageStorage(
    private val baseDir: Path,
) : CommunityMessageStorage {
    private val guildMap = AtomicLoadOnceMap<String, JsonCommunityMessageGuildStorage>()

    init {
        baseDir.resolve("guilds").createDirectories()
    }

    override fun storageForGuild(guildId: String): CommunityMessageGuildStorage {
        return guildMap.getOrPut(guildId) {
            val fs = zipFileSystemProvider().newFileSystem(
                baseDir.resolve("guilds").resolve("$guildId.zip"),
                ZIP_FS_OPTIONS,
            )

            try {
                JsonCommunityMessageGuildStorage(fs)
            } catch (e: Exception) {
                try {
                    fs.close()
                } catch (fsError: Exception) {
                    e.addSuppressed(fsError)
                }

                throw e
            }
        }
    }

    fun close() {
        guildMap.closeAndTake().values.forEach { it.close() }
    }
}
