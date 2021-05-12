package org.randomcat.agorabot.setup

import org.randomcat.agorabot.DefaultStartupMessageStrategy
import org.randomcat.agorabot.StartupMessageStrategy
import java.nio.file.Path

private fun BotDataPaths.startupMessageStoragePath(): Path {
    return storageDir().resolve("hammertime_channel")
}

fun setupStartupMessageStrategy(paths: BotDataPaths): StartupMessageStrategy {
    return DefaultStartupMessageStrategy(storagePath = paths.startupMessageStoragePath())
}
