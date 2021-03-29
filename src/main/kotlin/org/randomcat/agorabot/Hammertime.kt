package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.util.ignoringRestActionOn
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private fun Path.resolveHammertime() = resolve("hammertime_channel")

private val HAMMERTIME_FILE_CHARSET = Charsets.UTF_8

private val lock = ReentrantLock()

fun handleStartupMessage(basePath: Path, jda: JDA) {
    val hammertimePath = basePath.resolveHammertime()

    val hammertimeChannelId = lock.withLock {
        try {
            Files.readString(hammertimePath, HAMMERTIME_FILE_CHARSET)
        } catch (e: IOException) {
            // Who cares, this is a joke
            return
        } finally {
            Files.deleteIfExists(hammertimePath)
        }
    }

    ignoringRestActionOn(jda) {
        jda.getTextChannelById(hammertimeChannelId)?.sendMessage("hammertime")
    }.queue()
}

fun writeStartupMessageChannel(basePath: Path, channelId: String) {
    lock.withLock {
        Files.writeString(
            basePath.resolveHammertime(),
            channelId,
            HAMMERTIME_FILE_CHARSET,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE,
        )
    }
}
