package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.AtomicCachedStorage
import org.randomcat.agorabot.config.StorageStrategy
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap.StorageType
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap.ValueType
import java.nio.file.Path
import kotlin.properties.Delegates

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
        var updated by Delegates.notNull<Boolean>()

        impl.updateValue {
            if (!it.containsKey(channelId)) {
                updated = true
                it.put(channelId, gameId)
            } else {
                updated = false
                it
            }
        }

        return updated
    }

    override fun removeGameForChannelId(channelId: String) {
        impl.updateValue {
            it.remove(channelId)
        }
    }
}
