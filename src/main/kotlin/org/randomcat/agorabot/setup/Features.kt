package org.randomcat.agorabot.setup

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.config.CitationsConfig
import org.randomcat.agorabot.config.readCitationsConfig
import org.randomcat.agorabot.features.archiveCommandsFeature
import org.randomcat.agorabot.util.DefaultDiscordArchiver
import java.nio.file.Path

private fun BotDataPaths.featureConfigDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("features")
    }
}

fun setupCitationsConfig(paths: BotDataPaths): CitationsConfig? {
    return readCitationsConfig(paths.featureConfigDir().resolve("citations.json"))
}

private fun BotDataPaths.archiveStorageDir(): Path {
    return storageDir().resolve("stored_archives")
}

private fun setupArchiver(paths: BotDataPaths): DiscordArchiver {
    return DefaultDiscordArchiver(
        storageDir = paths.tempDir().resolve("archive"),
    )
}

fun setupArchiveFeature(paths: BotDataPaths): Feature {
    return archiveCommandsFeature(
        discordArchiver = setupArchiver(paths),
        localStorageDir = paths.archiveStorageDir(),
    )
}
