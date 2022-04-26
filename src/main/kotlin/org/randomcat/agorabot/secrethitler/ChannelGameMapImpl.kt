package org.randomcat.agorabot.secrethitler

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.randomcat.agorabot.config.persist.AtomicCachedStorage
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.config.persist.StorageStrategy
import org.randomcat.agorabot.config.persist.updateValueAndExtract
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap.StorageType
import org.randomcat.agorabot.secrethitler.model.SecretHitlerGameId
import org.randomcat.util.isDistinct
import org.randomcat.util.requireDistinct
import java.nio.file.Path

// It'll be fiiiiineee....
@Suppress("TOPLEVEL_TYPEALIASES_ONLY")
class JsonSecretHitlerChannelGameMap(
    storagePath: Path,
    persistService: ConfigPersistService
) : SecretHitlerChannelGameMap {
    private class ValueType private constructor(
        private val gameIdsByChannel: PersistentMap<String, SecretHitlerGameId>,
        private val channelsByGameId: PersistentMap<SecretHitlerGameId, String>,
    ) {
        companion object {
            fun fromMap(gameIdsByChannel: Map<String, SecretHitlerGameId>): ValueType {
                val forwardMap = gameIdsByChannel.toPersistentMap()
                forwardMap.values.requireDistinct()

                val reverseMap = forwardMap.entries.associate { it.value to it.key }.toPersistentMap()
                check(reverseMap.values.isDistinct())

                return ValueType(
                    gameIdsByChannel = forwardMap,
                    channelsByGameId = reverseMap,
                )
            }

            private val EMPTY = ValueType(
                gameIdsByChannel = persistentMapOf(),
                channelsByGameId = persistentMapOf(),
            )

            fun empty(): ValueType = EMPTY
        }

        init {
            require(gameIdsByChannel.keys == channelsByGameId.values.toSet())
            require(channelsByGameId.keys == gameIdsByChannel.values.toSet())
        }

        private fun containsChannelId(channelId: String): Boolean {
            return gameIdsByChannel.containsKey(channelId)
        }

        private fun containsGameId(gameId: SecretHitlerGameId): Boolean {
            return channelsByGameId.containsKey(gameId)
        }

        /**
         * If this contains neither the channel id [channelId] nor the game id [gameId], returns a pair containing a
         * value with a new entry with those values and `true`.
         * Otherwise, returns a pair containing a value equal to this and `false`.
         */
        fun tryPut(channelId: String, gameId: SecretHitlerGameId): Pair<ValueType, Boolean> {
            if (containsChannelId(channelId)) return this to false
            if (containsGameId(gameId)) return this to false

            return ValueType(
                gameIdsByChannel = gameIdsByChannel.put(channelId, gameId),
                channelsByGameId = channelsByGameId.put(gameId, channelId),
            ) to true
        }

        fun toGameIdsByChannelIdMap(): Map<String, SecretHitlerGameId> {
            return gameIdsByChannel
        }

        fun gameIdByChannelId(channelId: String): SecretHitlerGameId? {
            return gameIdsByChannel[channelId]
        }

        fun channelIdByGameId(gameId: SecretHitlerGameId): String? {
            return channelsByGameId[gameId]
        }

        /**
         * If this contains the channel id [channelId], returns a pair containing a value with it removed and `true`.
         * Otherwise, returns a pair containing a value equal to this and `false`.
         */
        fun removeByChannelId(channelId: String): Pair<ValueType, SecretHitlerGameId?> {
            // Need default value to satisfy compiler that it is initialized
            var removedGameId: SecretHitlerGameId? = null

            val newForwardMap = gameIdsByChannel.mutate {
                removedGameId = it.remove(channelId)
            }

            if (removedGameId == null) return this to null

            val newReverseMap = channelsByGameId.mutate { mutator ->
                mutator.entries.removeIf { it.value == channelId }
            }

            return ValueType(
                gameIdsByChannel = newForwardMap,
                channelsByGameId = newReverseMap,
            ) to removedGameId
        }

        override fun toString(): String {
            return gameIdsByChannel.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (other !is ValueType) return false
            return (this.gameIdsByChannel).equals(other.gameIdsByChannel)
        }

        override fun hashCode(): Int {
            return gameIdsByChannel.hashCode()
        }
    }

    private typealias StorageType = Map<String, SecretHitlerGameId>

    private object Strategy : StorageStrategy<ValueType> {
        override fun defaultValue(): ValueType {
            return ValueType.empty()
        }

        private fun ValueType.toStorage(): StorageType {
            return this.toGameIdsByChannelIdMap()
        }

        private fun StorageType.toValue(): ValueType {
            return ValueType.fromMap(gameIdsByChannel = this)
        }

        override fun encodeToString(value: ValueType): String {
            return Json.encodeToString<StorageType>(value.toStorage())
        }

        override fun decodeFromString(text: String): ValueType {
            return Json.decodeFromString<StorageType>(text).toValue()
        }
    }

    private val impl = AtomicCachedStorage<ValueType>(storagePath = storagePath, strategy = Strategy, persistService = persistService)

    override fun gameByChannelId(channelId: String): SecretHitlerGameId? {
        return impl.getValue().gameIdByChannelId(channelId)
    }

    override fun channelIdByGame(gameId: SecretHitlerGameId): String? {
        return impl.getValue().channelIdByGameId(gameId)
    }

    override fun tryPutGameForChannelId(channelId: String, gameId: SecretHitlerGameId): Boolean {
        return impl.updateValueAndExtract { value ->
            value.tryPut(
                channelId = channelId,
                gameId = gameId,
            )
        }
    }

    override fun removeGameForChannelId(channelId: String): SecretHitlerGameId? {
        return impl.updateValueAndExtract { value ->
            value.removeByChannelId(channelId)
        }
    }
}
