package org.randomcat.agorabot.util

fun <A : Any, B : Any> Pair<A?, B?>.coalesceNulls(): Pair<A, B>? {
    val first = first
    val second = second

    if (first != null && second != null) {
        return Pair<A, B>(first, second)
    }

    return null
}
