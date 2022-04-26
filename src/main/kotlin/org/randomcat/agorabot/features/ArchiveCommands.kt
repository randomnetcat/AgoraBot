package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.ArchiveCommand
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
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
fun archiveCommandsFactory() = object : FeatureSource {
    override val featureName: String
        get() = "archive"

    override fun readConfig(context: FeatureSetupContext): ArchiveCommandsConfig {
        return ArchiveCommandsConfig(
            archiver = setupArchiver(context.paths),
            localStorageDir = context.paths.archiveStorageDir,
        )
    }

    override fun createFeature(config: Any?): Feature {
        config as ArchiveCommandsConfig

        return Feature.ofCommands { context ->
            mapOf(
                "archive" to ArchiveCommand(
                    strategy = context.defaultCommandStrategy,
                    archiver = config.archiver,
                    localStorageDir = config.localStorageDir,
                )
            )
        }
    }
}
