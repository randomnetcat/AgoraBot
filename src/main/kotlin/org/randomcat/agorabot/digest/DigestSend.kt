package org.randomcat.agorabot.digest

import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.outputStream

interface DigestSendStrategy {
    fun sendDigest(digest: Digest, destination: String)
}

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC)

private val logger = LoggerFactory.getLogger("DigestSend")

abstract class CommandDigestSendStrategy : DigestSendStrategy {
    // Use File here because these files need to be normal operating system files in order to pass them to processes.
    abstract val executablePath: File
    abstract val storageDir: File

    final override fun sendDigest(digest: Digest, destination: String) {
        // Ensure that all usages are consistent
        val storageDir = storageDir
        val executablePath = executablePath

        val now = Instant.now()

        Files.createDirectories(storageDir.toPath())

        val outputLogDir = Files.createTempDirectory(
            storageDir.toPath(),
            DATE_TIME_FORMAT.format(now),
        ).toFile()

        val stdinFile = outputLogDir.resolve("stdin")

        createStdinStream(digest, destination, now).use { stdinStream ->
            stdinFile.toPath().outputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { fileStream ->
                stdinStream.copyTo(fileStream)
            }
        }

        val builder = ProcessBuilder(
            buildList {
                add(executablePath.absolutePath)
                addAll(commandArguments(digest, destination))
            },
        ).apply {
            redirectInput(stdinFile)
            redirectOutput(outputLogDir.resolve("stdout"))
            redirectError(outputLogDir.resolve("stderr"))

            environment().clear()
        }

        logger.info("Sending digest to $destination.")
        logger.info("Executing send command: ${builder.command()}. Standard stream outputs are logged in $outputLogDir.")

        val terminatedProcess = builder.start().onExit().get()
        logger.info("Exit status: ${terminatedProcess.exitValue()}")
    }

    protected abstract fun createStdinStream(digest: Digest, destination: String, now: Instant): InputStream
    protected abstract fun commandArguments(digest: Digest, destination: String): List<String>
}

private fun formatMimeMessage(
    digest: Digest,
    format: DigestFormat,
    destination: String,
    now: Instant,
): String {
    val subject = "Discord digest ${DATE_FORMAT.format(now)}"
    val content = format.format(digest)

    return """
        |To: $destination
        |From: AgoraBot <some.nonexistent.email@randomcat.org>
        |Subject: $subject
        |MIME-Version: 1.0
        |Content-Type: text/plain; charset=utf-8
    """.trimMargin("|") + "\n\n" + content
}

data class SsmtpDigestSendStrategy(
    private val digestFormat: DigestFormat,
    override val executablePath: File,
    private val configPath: File,
    override val storageDir: File,
) : CommandDigestSendStrategy() {
    override fun commandArguments(digest: Digest, destination: String): List<String> = listOf(
        "-C${configPath.absolutePath}",
        "-FAgoraBot",
        "-fAgoraBot",
        destination,
    )

    override fun createStdinStream(digest: Digest, destination: String, now: Instant): InputStream {
        return formatMimeMessage(
            digest,
            digestFormat,
            destination,
            now,
        ).byteInputStream(Charsets.UTF_8)
    }
}


data class MsmtpDigestSendStrategy(
    private val digestFormat: DigestFormat,
    override val executablePath: File,
    private val configPath: File,
    override val storageDir: File,
) : CommandDigestSendStrategy() {
    private val arguments = listOf(
        "-t",
        "--file=${configPath.absolutePath}"
    )

    override fun commandArguments(digest: Digest, destination: String): List<String> = arguments

    override fun createStdinStream(digest: Digest, destination: String, now: Instant): InputStream {
        return formatMimeMessage(
            digest,
            digestFormat,
            destination,
            now,
        ).byteInputStream(Charsets.UTF_8)
    }
}
