package org.randomcat.agorabot.buttons

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.SchedulableAtomicCachedStorage
import org.randomcat.agorabot.config.StorageStrategy
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

interface ButtonRequestDescriptor

const val BUTTON_INVALID_ID_RAW = "INVALID_BUTTON"

@JvmInline
value class ButtonRequestId(val raw: String)

data class ButtonRequestData(
    val descriptor: ButtonRequestDescriptor,
    val expiry: Instant,
)

interface ButtonRequestDataMap {
    fun tryGetRequestById(id: ButtonRequestId, timeForExpirationCheck: Instant): ButtonRequestDescriptor?
    fun putRequest(data: ButtonRequestData): ButtonRequestId
}

@Serializable
private data class ButtonRequestDataDto(
    val descriptor: ButtonRequestDescriptor,
    val expiryInstantString: String,
) {
    companion object {
        fun from(data: ButtonRequestData): ButtonRequestDataDto {
            return ButtonRequestDataDto(
                descriptor = data.descriptor,
                expiryInstantString = data.expiry.toString(),
            )
        }
    }

    fun parse(): ButtonRequestData {
        return ButtonRequestData(
            descriptor = descriptor,
            expiry = Instant.parse(expiryInstantString),
        )
    }
}

private typealias JsonValueType = PersistentMap<String, ButtonRequestData>
private typealias JsonStoredValueType = Map<String, ButtonRequestDataDto>

class JsonButtonRequestDataMap(
    storagePath: Path,
    serializersModule: SerializersModule,
    clock: Clock,
) : ButtonRequestDataMap {
    private val impl = run {
        // Provides an unambiguous name for serializersModule
        @Suppress("UnnecessaryVariable")
        val theSerializersModule = serializersModule

        SchedulableAtomicCachedStorage<JsonValueType>(
            storagePath,
            object : StorageStrategy<JsonValueType> {
                private fun JsonValueType.toStoredValue(): JsonStoredValueType {
                    return mapValues { (_, v) -> ButtonRequestDataDto.from(v) }
                }

                private fun JsonStoredValueType.toValue(): JsonValueType {
                    return mapValues { (_, v) -> v.parse() }.toPersistentMap()
                }

                private val jsonImpl = Json {
                    this@Json.serializersModule = theSerializersModule
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
        ).also { storage ->
            storage.schedulePeriodicUpdate(Duration.ofMinutes(5)) { oldValue ->
                val currentTime = clock.instant()

                oldValue.mutate { valueMutator ->
                    valueMutator.entries.removeIf { entry ->
                        entry.value.expiry < currentTime
                    }
                }
            }
        }
    }

    override fun tryGetRequestById(id: ButtonRequestId, timeForExpirationCheck: Instant): ButtonRequestDescriptor? {
        return impl.getValue()[id.raw]?.takeIf { it.expiry > timeForExpirationCheck }?.descriptor
    }

    private fun generateId(): ButtonRequestId = ButtonRequestId(UUID.randomUUID().toString())

    override fun putRequest(data: ButtonRequestData): ButtonRequestId {
        var currentID = generateId()

        impl.updateValue { old ->
            while (old.containsKey(currentID.raw)) {
                currentID = generateId()
            }

            old.put(currentID.raw, data)
        }

        return currentID
    }

    fun schedulePersistenceOn(persistService: ConfigPersistService) {
        impl.schedulePersistenceOn(persistService)
    }
}
