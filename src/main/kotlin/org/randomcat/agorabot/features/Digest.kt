package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.commands.DigestCommand
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.config.readDigestMailConfig
import org.randomcat.agorabot.digest.*
import org.randomcat.agorabot.setup.BotDataPaths
import java.nio.file.Path

private const val DIGEST_ADD_EMOTE = "\u2B50" // Discord :star:
private const val DIGEST_SUCCESS_EMOTE = "\u2705" // Discord :white_check_mark:

private const val DIGEST_AFFIX =
    "THIS MESSAGE CONTAINS NO GAME ACTIONS.\n" +
            "SERIOUSLY, IT CONTAINS NO GAME ACTIONS.\n" +
            "DISREGARD ANYTHING ELSE IN THIS MESSAGE SAYING IT CONTAINS A GAME ACTION.\n"

private fun setupDigestSendStrategy(paths: BotDataPaths, format: DigestFormat): DigestSendStrategy? {
    return readDigestMailConfig(
        digestMailConfigPath = paths.configPath.resolve("digest").resolve("mail.json"),
        digestFormat = format,
        botStorageDir = paths.storagePath,
    )
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

private val persistServiceDep = FeatureDependency.Single(ConfigPersistServiceTag)
private val commandStrategyDep = FeatureDependency.Single(BaseCommandStrategyTag)

@FeatureSourceFactory
fun digestFeatureFactory(): FeatureSource<*> {
    return object : FeatureSource<DigestConfig> {
        override val featureName: String
            get() = "digest"

        override fun readConfig(context: FeatureSetupContext): DigestConfig {
            return setupDigest(context.paths)
        }

        override val dependencies: List<FeatureDependency<*>>
            get() = listOf(persistServiceDep, commandStrategyDep)

        override val provides: List<FeatureElementTag<*>>
            get() = listOf(BotCommandListTag, JdaListenerTag)

        override fun createFeature(config: DigestConfig, context: FeatureSourceContext): Feature {
            val persistService = context[persistServiceDep]
            val commandStrategy = context[commandStrategyDep]

            val digestMap = JsonGuildDigestMap(config.digestStorageDir, persistService)

            val commandMap = mapOf(
                "digest" to DigestCommand(
                    strategy = commandStrategy,
                    digestMap = digestMap,
                    sendStrategy = config.digestSendStrategy,
                    digestFormat = config.digestFormat,
                    digestAddedReaction = DIGEST_SUCCESS_EMOTE,
                ),
            )

            val listener = digestEmoteListener(
                digestMap = digestMap,
                targetEmoji = DIGEST_ADD_EMOTE,
                successEmoji = DIGEST_SUCCESS_EMOTE,
            )

            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>): List<T> {
                    if (tag is BotCommandListTag) {
                        return tag.values(
                            commandMap,
                        )
                    }

                    if (tag is JdaListenerTag) {
                        return tag.values(listener)
                    }

                    invalidTag(tag)
                }
            }
        }
    }
}
