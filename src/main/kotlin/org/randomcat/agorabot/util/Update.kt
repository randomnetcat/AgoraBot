package org.randomcat.agorabot.util

@PublishedApi
internal object UpdateAndExtractUninit

inline fun <T, V> doUpdateAndExtract(runUpdate: ((T) -> T) -> Unit, crossinline mapper: (T) -> Pair<T, V>): V {
    var extractedValue: Any? = UpdateAndExtractUninit

    runUpdate {
        val result = mapper(it)
        extractedValue = result.second
        result.first
    }

    // Protect against a hostile runUpdate trying to change this with a race
    val finalValue = extractedValue

    check(finalValue !== UpdateAndExtractUninit)

    @Suppress("UNCHECKED_CAST")
    // This is known to be safe. It must either be a V or UpdateAndExtractUninit, and it's been checked to not be the
    // latter.
    return finalValue as V
}
