package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.ArchiveCommand
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.ofBaseCommandsConfig
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

private data class ArchiveCommandsConfig(
    val archiver: DiscordArchiver,
    val localStorageDir: Path,
)

@FeatureSourceFactory
fun archiveCommandsFactory(): FeatureSource<*> = FeatureSource.ofBaseCommandsConfig(
    name = "archive",
    readConfig = { context ->
        ArchiveCommandsConfig(
            archiver = setupArchiver(context.paths),
            localStorageDir = context.paths.archiveStorageDir,
        )
    },
) { strategy, config, _ ->
    mapOf(
        "archive" to ArchiveCommand(
            strategy = strategy,
            archiver = config.archiver,
            localStorageDir = config.localStorageDir,
        ),
    )
}
