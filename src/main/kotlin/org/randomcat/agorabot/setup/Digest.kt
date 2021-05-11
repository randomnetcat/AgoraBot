package org.randomcat.agorabot.setup

import org.randomcat.agorabot.config.ConfigPersistService
import org.randomcat.agorabot.config.readDigestSendStrategyConfig
import org.randomcat.agorabot.digest.*
import java.nio.file.Path

private fun BotDataPaths.digestStorageDir(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("digests")
    }
}

private fun setupDigestStorage(paths: BotDataPaths, persistService: ConfigPersistService): GuildMutableDigestMap {
    return JsonGuildDigestMap(paths.digestStorageDir(), persistService)
}

private fun BotDataPaths.digestSendStrategyConfigPath(): Path {
    return when (this) {
        is BotDataPaths.Version0 -> basePath.resolve("mail.json")
    }
}

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

private fun setupDigestSendStrategy(paths: BotDataPaths, format: DigestFormat): DigestSendStrategy? {
    return readDigestSendStrategyConfig(paths.digestSendStrategyConfigPath(), format)
}

class DigestSetupResult(
    val digestFormat: DigestFormat,
    val digestMap: GuildMutableDigestMap,
    val digestSendStrategy: DigestSendStrategy?,
)

fun setupDigest(paths: BotDataPaths, persistService: ConfigPersistService): DigestSetupResult {
    val digestFormat = AffixDigestFormat(
        prefix = DIGEST_AFFIX + "\n",
        baseFormat = SimpleDigestFormat(),
        suffix = "\n\n" + DIGEST_AFFIX,
    )

    val sendStrategy = setupDigestSendStrategy(paths, digestFormat)
    val digestMap = setupDigestStorage(paths, persistService)

    return DigestSetupResult(
        digestFormat = digestFormat,
        digestMap = digestMap,
        digestSendStrategy = sendStrategy,
    )
}
