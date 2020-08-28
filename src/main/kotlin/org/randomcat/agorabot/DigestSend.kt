package org.randomcat.agorabot

import org.randomcat.agorabot.commands.MessageDigest
import org.randomcat.agorabot.commands.MessageDigestSendStrategy
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SsmtpDigestSendStrategy : MessageDigestSendStrategy {
    override fun sendDigest(digest: MessageDigest, destination: String) {
        val tempFile = Files.createTempFile("agorabot", "ssmtp-digest-data")!!

        val subject = "Discord digest ${DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.now())}"
        val content = digest.render()

        val messageText = "To: $destination\nSubject: $subject\n\n$content"

        Files.write(tempFile, messageText.toByteArray(Charsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING)

        ProcessBuilder("ssmtp", destination)
            .redirectInput(ProcessBuilder.Redirect.from(tempFile.toFile()))
            .start()
    }
}
