package org.randomcat.agorabot.buttons.impl

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.randomcat.agorabot.buttons.ButtonRequestData
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.SchedulableAtomicCachedStorage
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

enum class ButtonDataStorageVersion {
    JSON_VALUES_INLINE,
    JSON_VALUES_STRINGS,
}

@Serializable
private data class ButtonRequestDataDtoV1(
    val descriptor: ButtonRequestDescriptor,
    val expiryInstantString: String,
)

@Serializable
private data class ButtonRequestDataDtoV2(
    // Store descriptors in strings so that they can be deserialized individually. If they were stored inline, a single
    // descriptor that became invalid would cause the whole storage to fail to deserialize (this is what was done in
    // a previous version).
    val descriptorString: String,
    val expiryInstantString: String,
)

private fun convertVersions(
    oldContent: String,
    serializersModule: SerializersModule,
    oldVersion: ButtonDataStorageVersion,
    newVersion: ButtonDataStorageVersion,
): String {
    if (oldVersion == newVersion) return oldContent
    require(oldVersion.ordinal < newVersion.ordinal)

    return when (oldVersion) {
        ButtonDataStorageVersion.JSON_VALUES_INLINE -> {
            val jsonImpl = Json {
                this@Json.serializersModule = serializersModule
            }

            convertVersions(
                oldContent = Json.encodeToString<Map<String, ButtonRequestDataDtoV2>>(
                    jsonImpl.decodeFromString<Map<String, ButtonRequestDataDtoV1>>(oldContent).mapValues { (_, v) ->
                        ButtonRequestDataDtoV2(
                            descriptorString = jsonImpl.encodeToString<ButtonRequestDescriptor>(v.descriptor),
                            expiryInstantString = v.expiryInstantString,
                        )
                    },
                ),
                serializersModule = serializersModule,
                oldVersion = ButtonDataStorageVersion.JSON_VALUES_STRINGS,
                newVersion = newVersion,
            )
        }

        ButtonDataStorageVersion.JSON_VALUES_STRINGS -> {
            error("Newest version, should be unreachable")
        }
    }
}

private val logger = LoggerFactory.getLogger("AgoraBotJsonButtonRequestDataMap")

fun migrateButtonsStorage(
    storagePath: Path,
    serializersModule: SerializersModule,
    oldVersion: ButtonDataStorageVersion,
    newVersion: ButtonDataStorageVersion,
) {
    if (oldVersion == newVersion) return

    val buttonDataText = try {
        Files.readString(storagePath)
    } catch (e: NoSuchFileException) {
        return
    }

    logger.info("Migrating button storage from $oldVersion to $newVersion.")

    val newData = convertVersions(
        oldContent = buttonDataText,
        serializersModule = serializersModule,
        oldVersion = oldVersion,
        newVersion = newVersion,
    )

    Files.writeString(storagePath, newData)
}

private data class ButtonRequestDataUnparsedDescriptor(
    val expiry: Instant,
    val descriptorString: String,
)

private typealias JsonValueType = PersistentMap<String, ButtonRequestDataUnparsedDescriptor>
private typealias JsonStoredValueType = Map<String, ButtonRequestDataDtoV2>

class JsonButtonRequestDataMap(
    storagePath: Path,
    serializersModule: SerializersModule,
    clock: Clock,
    persistService: ConfigPersistService,
) : ButtonRequestDataMap {
    private val jsonImpl = Json {
        this@Json.serializersModule = serializersModule
    }

    private val impl = SchedulableAtomicCachedStorage<JsonValueType>(
        storagePath,
        object : StorageStrategy<JsonValueType> {
            private fun JsonValueType.toStoredValue(): JsonStoredValueType {
                return mapValues { (_, v) ->
                    ButtonRequestDataDtoV2(
                        expiryInstantString = v.expiry.toString(),
                        descriptorString = v.descriptorString,
                    )
                }
            }

            private fun JsonStoredValueType.toValue(): JsonValueType {
                return mapValues { (_, v) ->
                    ButtonRequestDataUnparsedDescriptor(
                        expiry = Instant.parse(v.expiryInstantString),
                        descriptorString = v.descriptorString,
                    )
                }.toPersistentMap()
            }

            override fun defaultValue(): JsonValueType {
                return persistentMapOf()
            }

            override fun encodeToString(value: JsonValueType): String {
                return jsonImpl.encodeToString<JsonStoredValueType>(value.toStoredValue())
            }

            override fun decodeFromString(text: String): JsonValueType {
                return jsonImpl.decodeFromString<JsonStoredValueType>(text).toValue()
            }
        },
        persistService,
    )

    init {
        impl.schedulePeriodicUpdate(Duration.ofMinutes(5)) { oldValue ->
            val currentTime = clock.instant()

            oldValue.mutate { valueMutator ->
                valueMutator.entries.removeIf { entry ->
                    entry.value.expiry < currentTime
                }
            }
        }
    }

    override fun tryGetRequestById(id: ButtonRequestId, timeForExpirationCheck: Instant): ButtonRequestDescriptor? {
        return impl.getValue()[id.raw]?.takeIf { it.expiry > timeForExpirationCheck }?.let {
            try {
                jsonImpl.decodeFromString<ButtonRequestDescriptor>(it.descriptorString)
            } catch (e: SerializationException) {
                null
            }
        }
    }

    private fun generateId(): ButtonRequestId = ButtonRequestId(UUID.randomUUID().toString())

    override fun putRequest(data: ButtonRequestData): ButtonRequestId {
        var currentID = generateId()

        val dataUnparsed = ButtonRequestDataUnparsedDescriptor(
            expiry = data.expiry,
            descriptorString = jsonImpl.encodeToString<ButtonRequestDescriptor>(data.descriptor),
        )

        impl.updateValue { old ->
            while (old.containsKey(currentID.raw)) {
                currentID = generateId()
            }

            old.put(currentID.raw, dataUnparsed)
        }

        return currentID
    }

    fun close() {
        impl.close()
    }
}
