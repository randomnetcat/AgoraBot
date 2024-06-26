package org.randomcat.agorabot.util

fun sequentiallyClose(blocks: Iterable<() -> Unit>) {
    var out: Throwable? = null

    for (block in blocks) {
        try {
            block()
        } catch (t: Throwable) {
            if (out != null) {
                out.addSuppressed(t)
            } else {
                out = t
            }
        }
    }

    if (out != null) throw out
}

fun sequentiallyClose(vararg blocks: () -> Unit) = sequentiallyClose(blocks.asIterable())

fun exceptionallyClose(t: Throwable, vararg blocks: () -> Unit): Nothing {
    for (block in blocks) {
        try {
            block()
        } catch (inner: Throwable) {
            t.addSuppressed(inner)
        }
    }

    throw t
}
