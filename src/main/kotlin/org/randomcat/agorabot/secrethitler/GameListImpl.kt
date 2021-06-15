package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.AtomicCachedStorage
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.StorageStrategy
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList.StorageType
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList.ValueType
import java.nio.file.Path
import java.util.*
import kotlin.properties.Delegates

@Serializable
private sealed class GameStateDto {
    companion object {
        fun from(gameState: SecretHitlerGameState): GameStateDto {
            return when (gameState) {
                is SecretHitlerGameState.Joining -> {
                    GameStateDto.Joining(names = gameState.playerNames.map { it.raw })
                }
            }
        }
    }

    abstract fun toGameState(): SecretHitlerGameState

    @Serializable
    data class Joining(val names: List<String>) : GameStateDto() {
        override fun toGameState(): SecretHitlerGameState {
            return SecretHitlerGameState.Joining(
                playerNames = names.map { SecretHitlerPlayerExternalName(it) }.toImmutableSet(),
            )
        }
    }
}


// It'll be fiiiiineee....
@Suppress("TOPLEVEL_TYPEALIASES_ONLY")
class JsonSecretHitlerGameList(storagePath: Path) : SecretHitlerGameList {
    private typealias ValueType = PersistentMap<SecretHitlerGameId, SecretHitlerGameState>
    private typealias StorageType = Map<SecretHitlerGameId, GameStateDto>

    private object Strategy : StorageStrategy<ValueType> {
        override fun defaultValue(): ValueType {
            return persistentMapOf()
        }

        private fun ValueType.toStorage(): StorageType {
            return mapValues { (_, v) -> GameStateDto.from(v) }
        }

        private fun StorageType.toValue(): ValueType {
            return mapValues { (_, v) -> v.toGameState() }.toPersistentMap()
        }

        override fun encodeToString(value: ValueType): String {
            return Json.encodeToString<StorageType>(value.toStorage())
        }

        override fun decodeFromString(text: String): ValueType {
            return Json.decodeFromString<StorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage(storagePath = storagePath, strategy = Strategy)

    override fun gameById(id: SecretHitlerGameId): SecretHitlerGameState? {
        return impl.getValue()[id]
    }

    private fun generateId(): SecretHitlerGameId {
        return SecretHitlerGameId(UUID.randomUUID().toString())
    }

    override fun createGame(state: SecretHitlerGameState): SecretHitlerGameId {
        var id = generateId()

        impl.updateValue {
            while (it.containsKey(id)) {
                id = generateId()
            }

            it.put(id, state)
        }

        return id
    }

    override fun removeGameIfExists(id: SecretHitlerGameId) {
        impl.updateValue {
            it.remove(id)
        }
    }

    override fun updateGame(id: SecretHitlerGameId, mapper: (SecretHitlerGameState) -> SecretHitlerGameState): Boolean {
        var updated by Delegates.notNull<Boolean>()

        impl.updateValue {
            val old = it[id]

            if (old != null) {
                updated = true
                it.put(id, mapper(old))
            } else {
                updated = false
                it
            }
        }

        return updated
    }

    fun schedulePersistenceOn(persistService: ConfigPersistService) {
        impl.schedulePersistenceOn(persistService)
    }
}
