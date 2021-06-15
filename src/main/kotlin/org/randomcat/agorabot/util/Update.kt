package org.randomcat.agorabot.util

@PublishedApi
@JvmField
internal val UNSET_UPDATED_VALUE = Any()

inline fun <T, V> doUpdateAndExtract(runUpdate: ((T) -> T) -> Unit, crossinline mapper: (T) -> Pair<T, V>): V {
    var extractedValue: Any? = UNSET_UPDATED_VALUE

    runUpdate {
        val result = mapper(it)
        extractedValue = result.second
        result.first
    }

    // Protect against a hostile runUpdate trying to change this with a race
    val finalValue = extractedValue

    check(finalValue !== UNSET_UPDATED_VALUE)

    @Suppress("UNCHECKED_CAST")
    return finalValue as V
}
