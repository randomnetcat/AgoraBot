package org.randomcat.agorabot.guild_state

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface GuildState {
    fun getStrings(keys: List<String>): List<String?>
    fun getString(key: String): String? = getStrings(listOf(key)).single()

    fun setStrings(keys: List<String>, values: List<String>)
    fun setString(key: String, value: String) = setStrings(listOf(key), listOf(value))

    fun updateStrings(keys: List<String>, mapper: (old: List<String?>) -> List<String>)

    fun updateString(key: String, mapper: (old: String?) -> String) = updateStrings(listOf(key)) { values ->
        listOf(mapper(values.single()))
    }
}

inline fun <reified T> GuildState.get(key: String): T? {
    return getString(key)?.let { Json.decodeFromString<T>(it) }
}

inline fun <reified T> GuildState.getMany(keys: List<String>): List<T?> {
    return getStrings(keys).map { str -> str?.let { Json.decodeFromString<T>(it) } }
}

inline fun <reified T> GuildState.set(key: String, value: T) {
    setString(key, Json.encodeToString<T>(value))
}

inline fun <reified T> GuildState.setMany(keys: List<String>, values: List<T>) {
    setStrings(keys, values.map { Json.encodeToString<T>(it) })
}

inline fun <reified T> GuildState.update(key: String, crossinline mapper: (old: T?) -> T) {
    updateString(key) { oldString ->
        Json.encodeToString<T>(mapper(oldString?.let { Json.decodeFromString<T>(it) }))
    }
}

inline fun <reified T> GuildState.updateMany(keys: List<String>, crossinline mapper: (old: List<T?>) -> List<T>) {
    updateStrings(keys) { oldStrings ->
        mapper(
            oldStrings.map { oldString ->
                oldString?.let { Json.decodeFromString<T>(it) }
            }
        ).map {
            Json.encodeToString<T>(it)
        }
    }
}

interface GuildStateMap {
    fun stateForGuild(guildId: String): GuildState
}
