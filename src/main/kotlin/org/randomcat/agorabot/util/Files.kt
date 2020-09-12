package org.randomcat.agorabot.util

import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates a temp file, invokes [block] with it, then deletes it (even if [block] throws).
 *
 * @param comment a comment on the use of the file - its usage is unspecified
 */
inline fun <R> withTempFile(comment: String? = null, block: (Path) -> R): R {
    val tempFile: Path = Files.createTempFile("agorabot", comment)

    return try {
        block(tempFile)
    } finally {
        Files.deleteIfExists(tempFile)
    }
}
