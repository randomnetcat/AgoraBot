package org.randomcat.agorabot.setup

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.DiscordArchiver
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.config.parsing.features.SecretHitlerFeatureConfig
import org.randomcat.agorabot.config.parsing.features.readCitationsConfig
import org.randomcat.agorabot.config.parsing.features.readSecretHitlerConfig
import org.randomcat.agorabot.features.archiveCommandsFeature
import org.randomcat.agorabot.features.secretHitlerFeature
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList
import org.randomcat.agorabot.secrethitler.SecretHitlerJsonImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.util.DefaultDiscordArchiver
import java.nio.file.Files
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

private fun setupSecretHitlerConfig(paths: BotDataPaths): SecretHitlerFeatureConfig? {
    return readSecretHitlerConfig(paths.featureConfigDir().resolve("secret_hitler.json"))
}

fun setupSecretHitlerFeature(paths: BotDataPaths, persistService: ConfigPersistService): Feature? {
    val config = setupSecretHitlerConfig(paths) ?: return null

    val secretHitlerDir = paths.storagePath.resolve("secret_hitler")
    Files.createDirectories(secretHitlerDir)

    val gameList = JsonSecretHitlerGameList(storagePath = secretHitlerDir.resolve("games"))
    gameList.schedulePersistenceOn(persistService)

    val channelGameMap = JsonSecretHitlerChannelGameMap(storagePath = secretHitlerDir.resolve("games_by_channel"))
    channelGameMap.schedulePersistenceOn(persistService)

    val repository = SecretHitlerRepository(
        gameList = gameList,
        channelGameMap = channelGameMap,
    )

    val impersonationMap = if (config.enableImpersonation) {
        SecretHitlerJsonImpersonationMap(secretHitlerDir.resolve("impersonation_data")).also {
            it.schedulePersistenceOn(persistService)
        }
    } else {
        null
    }

    return secretHitlerFeature(repository = repository, impersonationMap = impersonationMap)
}
