package org.randomcat.agorabot.util

import java.util.concurrent.atomic.AtomicReference

private object MapUninit

fun <T, R> AtomicReference<T>.updateAndMap(block: (old: T) -> Pair<T, R>): R {
    var mapResult: Any? = MapUninit

    updateAndGet { old ->
        val blockResult = block(old)
        mapResult = blockResult.second
        blockResult.first
    }

    // Use identity equals to defend against hostile user-defined equals
    check(mapResult !== MapUninit)

    @Suppress("UNCHECKED_CAST")
    return mapResult as R
}
