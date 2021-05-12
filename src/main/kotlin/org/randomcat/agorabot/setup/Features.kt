package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.CitationsConfig
import org.randomcat.agorabot.config.readCitationsConfig
import java.nio.file.Path

private fun BotDataPaths.featureConfigDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("features")
    }
}

fun setupCitationsConfig(paths: BotDataPaths): CitationsConfig? {
    return readCitationsConfig(paths.featureConfigDir().resolve("citations.json"))
}
