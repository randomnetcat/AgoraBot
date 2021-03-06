package org.randomcat.agorabot.setup

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.config.parsing.features.readCitationsConfig
import org.randomcat.agorabot.features.archiveCommandsFeature
import org.randomcat.agorabot.util.DefaultDiscordArchiver
import java.nio.file.Path

private fun BotDataPaths.featureConfigDir(): Path {
    return configPath.resolve("features")
}

fun setupCitationsConfig(paths: BotDataPaths): CitationsConfig? {
    return readCitationsConfig(paths.featureConfigDir().resolve("citations.json"))
}

private fun BotDataPaths.archiveStorageDir(): Path {
    return storagePath.resolve("stored_archives")
}

private fun setupArchiver(paths: BotDataPaths): DiscordArchiver {
    return DefaultDiscordArchiver(
        storageDir = paths.tempPath.resolve("archive"),
    )
}

fun setupArchiveFeature(paths: BotDataPaths): Feature {
    return archiveCommandsFeature(
        discordArchiver = setupArchiver(paths),
        localStorageDir = paths.archiveStorageDir(),
    )
}
