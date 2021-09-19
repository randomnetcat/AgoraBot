package org.randomcat.agorabot.setup.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.parsing.features.SecretHitlerFeatureConfig
import org.randomcat.agorabot.config.parsing.features.readSecretHitlerConfig
import org.randomcat.agorabot.features.secretHitlerFeature
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerChannelGameMap
import org.randomcat.agorabot.secrethitler.JsonSecretHitlerGameList
import org.randomcat.agorabot.secrethitler.SecretHitlerJsonImpersonationMap
import org.randomcat.agorabot.secrethitler.SecretHitlerRepository
import org.randomcat.agorabot.setup.BotDataPaths
import java.nio.file.Files


private fun setupSecretHitlerConfig(paths: BotDataPaths): SecretHitlerFeatureConfig? {
    return readSecretHitlerConfig(paths.featureConfigDir.resolve("secret_hitler.json"))
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
