package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.ArchiveCommand
import org.randomcat.agorabot.commands.DiscordArchiver
import java.nio.file.Path

fun archiveCommandsFeature(
    discordArchiver: DiscordArchiver,
    localStorageDir: Path,
) = Feature.ofCommands { context ->
    mapOf(
        "archive" to ArchiveCommand(
            strategy = context.defaultCommandStrategy,
            archiver = discordArchiver,
            localStorageDir = localStorageDir,
        )
    )
}
