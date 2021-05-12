package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.util.ignoringRestActionOn
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val HAMMERTIME_FILE_CHARSET = Charsets.UTF_8

private val GLOBAL_HAMMERTIME_LOCK = ReentrantLock()

interface StartupMessageStrategy {
    fun writeChannel(channelId: String)
    fun sendMessageAndClearChannel(jda: JDA)
}

class DefaultStartupMessageStrategy(private val storagePath: Path) : StartupMessageStrategy {
    override fun writeChannel(channelId: String) {
        GLOBAL_HAMMERTIME_LOCK.withLock {
            try {
                Files.writeString(
                    storagePath,
                    channelId,
                    HAMMERTIME_FILE_CHARSET,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE,
                )
            } catch (e: IOException) {
                // Who cares.
                return
            }
        }
    }

    override fun sendMessageAndClearChannel(jda: JDA) {
        val hammertimeChannelId = GLOBAL_HAMMERTIME_LOCK.withLock {
            try {
                Files.readString(storagePath, HAMMERTIME_FILE_CHARSET)
            } catch (e: IOException) {
                // Who cares, this is a joke
                return
            } finally {
                Files.deleteIfExists(storagePath)
            }
        }

        ignoringRestActionOn(jda) {
            jda.getTextChannelById(hammertimeChannelId)?.sendMessage("hammertime")
        }.queue()
    }
}
