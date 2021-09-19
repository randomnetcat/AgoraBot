package org.randomcat.agorabot.setup.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.features.archiveCommandsFeature
import org.randomcat.agorabot.setup.BotDataPaths
import org.randomcat.agorabot.util.DefaultDiscordArchiver
import java.nio.file.Path

private val BotDataPaths.archiveStorageDir: Path
    get() = storagePath.resolve("stored_archives")

private fun setupArchiver(paths: BotDataPaths): DiscordArchiver {
    return DefaultDiscordArchiver(
        storageDir = paths.tempPath.resolve("archive"),
    )
}

fun setupArchiveFeature(paths: BotDataPaths): Feature {
    return archiveCommandsFeature(
        discordArchiver = setupArchiver(paths),
        localStorageDir = paths.archiveStorageDir,
    )
}
