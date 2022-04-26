package org.randomcat.agorabot.util

import java.io.Writer

private class NonClosingWriter(private val impl: Writer) : Writer() {
    override fun write(c: Int) {
        return impl.write(c)
    }

    override fun write(cbuf: CharArray) {
        return impl.write(cbuf)
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        return impl.write(cbuf, off, len)
    }

    override fun write(str: String) {
        return impl.write(str)
    }

    override fun write(str: String, off: Int, len: Int) {
        return impl.write(str, off, len)
    }

    override fun append(csq: CharSequence?): Writer {
        impl.append(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
        impl.append(csq, start, end)
        return this
    }

    override fun append(c: Char): Writer {
        impl.append(c)
        return this
    }

    override fun flush() {
        return impl.flush()
    }

    override fun close() {
        // Do nothing, this is a non-closing view
    }
}

/**
 * Returns a [Writer] that behaves as the receiver in all ways, except that close() is a no-op.
 */
fun Writer.nonClosingView(): Writer {
    return NonClosingWriter(this)
}
