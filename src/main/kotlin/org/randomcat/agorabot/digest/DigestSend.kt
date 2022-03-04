package org.randomcat.agorabot.digest

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface DigestSendStrategy {
    fun sendDigest(digest: Digest, destination: String)
}

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-SS").withZone(ZoneOffset.UTC)

private val logger = LoggerFactory.getLogger("DigestSend")

data class SsmtpDigestSendStrategy(
    private val digestFormat: DigestFormat,
    private val executablePath: Path,
    private val configPath: Path,
    private val storageDir: Path,
) : DigestSendStrategy {
    init {
        Files.createDirectories(storageDir)
    }

    override fun sendDigest(digest: Digest, destination: String) {
        val now = Instant.now()

        val subject = "Discord digest ${DATE_FORMAT.format(now)}"
        val content = digestFormat.format(digest)

        val messageText =
            "To: $destination\n" +
                    "Subject: $subject\n" +
                    "MIME-Version: 1.0\n" +
                    "Content-Type: text/plain; charset=utf-8" +
                    "\n\n" +
                    content

        val outputLogDir = Files.createTempDirectory(
            storageDir,
            DATE_TIME_FORMAT.format(now),
        )

        val stdinFile = outputLogDir.resolve("stdin")

        Files.writeString(stdinFile,
            messageText,
            Charsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.TRUNCATE_EXISTING)

        val builder = ProcessBuilder(
            executablePath.toAbsolutePath().toString(),
            "-C${configPath.toAbsolutePath()}",
            "-FAgoraBot",
            "-fAgoraBot",
            destination
        )
            .redirectInput(ProcessBuilder.Redirect.from(stdinFile.toFile()))
            .redirectOutput(ProcessBuilder.Redirect.to(outputLogDir.resolve("stdout").toFile()))
            .redirectError(ProcessBuilder.Redirect.to(outputLogDir.resolve("stderr").toFile()))

        logger.info("Sending digest to $destination.")
        logger.info("Executing SSMTP command: ${builder.command()}. Standard stream outputs are logged in $outputLogDir.")

        val terminatedProcess = builder.start().onExit().get()
        logger.info("Exit status: ${terminatedProcess.exitValue()}")
    }
}
