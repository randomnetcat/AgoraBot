package org.randomcat.agorabot.digest

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface DigestSendStrategy {
    fun sendDigest(digest: Digest, destination: String)
}

class SsmtpDigestSendStrategy(
    private val digestFormat: DigestFormat,
    private val executablePath: Path,
    private val configPath: Path,
) : DigestSendStrategy {
    override fun sendDigest(digest: Digest, destination: String) {
        val tempFile = Files.createTempFile("agorabot", "ssmtp-digest-data")!!

        val subject = "Discord digest ${DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now())}"
        val content = digestFormat.format(digest)

        val messageText = "To: $destination\nSubject: $subject\n\n$content"

        Files.writeString(tempFile, messageText, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)

        ProcessBuilder(
            executablePath.toAbsolutePath().toString(),
            "-C${configPath.toAbsolutePath()}",
            "-FAgoraBot",
            "-fAgoraBot",
            destination
        )
            .redirectInput(ProcessBuilder.Redirect.from(tempFile.toFile()))
            .start()
    }
}
