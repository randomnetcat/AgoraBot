package org.randomcat.agorabot.util

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

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

fun deleteRecursively(path: Path) {
    try {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })

        Files.deleteIfExists(path)
    } catch (e: NoSuchFileException) {
        /* ignored */
    }
}
