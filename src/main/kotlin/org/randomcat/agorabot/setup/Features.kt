package org.randomcat.agorabot.setup

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.config.parsing.features.readCitationsConfig
import org.randomcat.agorabot.features.archiveCommandsFeature
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
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

fun setupSecretHitlerFeature(paths: BotDataPaths): SecretHitlerRepository {
    val secretHitlerDir = paths.storagePath.resolve("secret_hitler")

    return SecretHitlerRepository(
        gameList = JsonSecretHitlerGameList(storagePath = secretHitlerDir.resolve("games")),
        channelGameMap = JsonSecretHitlerChannelGameMap(storagePath = secretHitlerDir.resolve("games_by_channel")),
    )
}
