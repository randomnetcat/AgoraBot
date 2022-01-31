package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.config.persist.feature.configPersistService
import org.randomcat.agorabot.config.readDigestMailConfig
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.setup.BotDataPaths
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:
private const val DIGEST_SUCCESS_EMOTE = "\u2705" // Discord :white_check_mark:

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

private fun setupDigestSendStrategy(paths: BotDataPaths, format: DigestFormat): DigestSendStrategy? {
    return readDigestMailConfig(paths.configPath.resolve("digest").resolve("mail.json"), format)
}

private data class DigestConfig(
    val digestStorageDir: Path,
    val digestFormat: DigestFormat,
    val digestSendStrategy: DigestSendStrategy?,
)

private fun setupDigest(paths: BotDataPaths): DigestConfig {
    val digestFormat = AffixDigestFormat(
        prefix = DIGEST_AFFIX + "\n",
        baseFormat = SimpleDigestFormat(),
        suffix = "\n\n" + DIGEST_AFFIX,
    )

    val sendStrategy = setupDigestSendStrategy(paths, digestFormat)

    return DigestConfig(
        digestStorageDir = paths.storagePath.resolve("digests"),
        digestFormat = digestFormat,
        digestSendStrategy = sendStrategy,
    )
}

private object DigestStorageCacheKey

@FeatureSourceFactory
fun digestFeatureFactory(): FeatureSource {
    return object : FeatureSource {
        override val featureName: String
            get() = "digest"

        override fun readConfig(context: FeatureSetupContext): DigestConfig {
            return setupDigest(context.paths)
        }

        override fun createFeature(config: Any?): Feature {
            val typedConfig = config as DigestConfig

            return object : AbstractFeature() {
                private val FeatureContext.digestMap
                    get() = cache(DigestStorageCacheKey) {
                        JsonGuildDigestMap(typedConfig.digestStorageDir, configPersistService)
                    }

                override fun commandsInContext(context: FeatureContext): Map<String, Command> {
                    return mapOf(
                        "digest" to DigestCommand(
                            strategy = context.defaultCommandStrategy,
                            digestMap = context.digestMap,
                            sendStrategy = typedConfig.digestSendStrategy,
                            digestFormat = typedConfig.digestFormat,
                            digestAddedReaction = DIGEST_SUCCESS_EMOTE,
                        ),
                    )
                }

                override fun jdaListeners(context: FeatureContext): List<Any> {
                    return listOf(
                        digestEmoteListener(
                            digestMap = context.digestMap,
                            targetEmoji = DIGEST_ADD_EMOTE,
                            successEmoji = DIGEST_SUCCESS_EMOTE,
                        ),
                    )
                }
            }
        }
    }
}
