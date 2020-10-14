package org.randomcat.agorabot.util

fun <T, U : T> Iterable<U>.repeat(times: Int): List<T> {
    val source = this // For clarity
    val effectiveSize = if (this is Collection) this.size else 10

    return ArrayList<T>(/* capacity = */ effectiveSize * times).also { res ->
        repeat(times) {
            res.addAll(source)
        }
    }
}
