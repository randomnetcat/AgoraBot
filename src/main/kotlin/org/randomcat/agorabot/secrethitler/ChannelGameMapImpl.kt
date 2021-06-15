package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.AtomicCachedStorage
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.StorageStrategy
import org.randomcat.agorabot.config.updateValueAndExtract
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap.StorageType
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap.ValueType
import java.nio.file.Path

// It'll be fiiiiineee....
@Suppress("TOPLEVEL_TYPEALIASES_ONLY")
class JsonSecretHitlerChannelGameMap(storagePath: Path) : SecretHitlerChannelGameMap {
    private typealias ValueType = PersistentMap<String, SecretHitlerGameId>
    private typealias StorageType = Map<String, SecretHitlerGameId>

    private object Strategy : StorageStrategy<ValueType> {
        override fun defaultValue(): ValueType {
            return persistentMapOf()
        }

        private fun ValueType.toStorage(): StorageType {
            return this
        }

        private fun StorageType.toValue(): ValueType {
            return toPersistentMap()
        }

        override fun encodeToString(value: ValueType): String {
            return Json.encodeToString<StorageType>(value.toStorage())
        }

        override fun decodeFromString(text: String): ValueType {
            return Json.decodeFromString<StorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage<ValueType>(storagePath = storagePath, strategy = Strategy)

    override fun gameByChannelId(channelId: String): SecretHitlerGameId? {
        return impl.getValue()[channelId]
    }

    override fun tryPutGameForChannelId(channelId: String, gameId: SecretHitlerGameId): Boolean {
        return impl.updateValueAndExtract {
            if (!it.containsKey(channelId)) {
                it.put(channelId, gameId) to true
            } else {
                it to false
            }
        }
    }

    override fun removeGameForChannelId(channelId: String): SecretHitlerGameId? {
        return impl.updateValueAndExtract { map ->
            var removed: SecretHitlerGameId? = null

            val newMap = map.mutate { mutator ->
                removed = mutator.remove(channelId)
            }

            newMap to removed
        }
    }

    fun schedulePersistenceOn(persistService: ConfigPersistService) {
        impl.schedulePersistenceOn(persistService)
    }
}
