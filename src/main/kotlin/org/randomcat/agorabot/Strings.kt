package org.randomcat.agorabot

/**
 * Splits an arguments string:
 * - Splits on spaces, except between quotation marks.
 * - Characters escaped by backslash are included verbatim (without a preceding backslash).
 * - Removes empty strings from the output.
 */
fun splitArguments(string: String): List<String> {
    if (!string.contains("\"") && !string.contains("\\")) return string.split(" ").filter { it.isNotEmpty() }

    val previousArgs = mutableListOf<String>()
    val currentString: StringBuilder = StringBuilder()

    var isEscape: Boolean = false
    var isQuoted: Boolean = false
    var isArgument: Boolean = false

    fun addChar(c: Char) {
        isArgument = true
        currentString.append(c)
    }

    fun commitArg() {
        if (isArgument) {
            previousArgs.add(currentString.toString())
        }

        currentString.clear()
        isEscape = false
        isQuoted = false
        isArgument = false
    }

    for (c in string) {
        if (isEscape) {
            addChar(c)
            isEscape = false
            continue
        }

        if (c == '\\') {
            isEscape = true
            continue
        }

        if (c == '"') {
            isQuoted = !isQuoted
            // Including quotes shows intention to make this an argument, even if empty.
            if (isQuoted) isArgument = true
            continue
        }

        if (!isQuoted && c == ' ') {
            commitArg()
            continue
        }

        addChar(c)
    }

    commitArg()

    return previousArgs
}
