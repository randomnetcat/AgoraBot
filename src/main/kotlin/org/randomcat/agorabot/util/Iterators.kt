package org.randomcat.agorabot.util

fun <T, I : Iterator<T>> I.consumeFirst(count: Int): Pair<List<T>, I?> {
    val out = ArrayList<T>(count)

    repeat(count) { _ ->
        if (hasNext()) {
            out.add(next())
        } else {
            return out to null
        }
    }

    return out to this
}
