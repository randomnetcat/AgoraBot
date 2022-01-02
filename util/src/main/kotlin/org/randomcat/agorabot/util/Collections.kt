package org.randomcat.agorabot.util

fun <T, U : T> Iterable<U>.repeated(times: Int): List<T> {
    val source = this // For clarity
    val result = if (this is Collection) ArrayList<T>(/* capacity = */ this.size * times) else ArrayList<T>()

    repeat(times) {
        result.addAll(source)
    }

    return result
}
