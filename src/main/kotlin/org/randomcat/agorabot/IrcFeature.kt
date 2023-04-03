package org.randomcat.agorabot

import org.kitteh.irc.client.library.feature.auth.SaslEcdsaNist256PChallenge
import org.randomcat.agorabot.config.parsing.RelayIrcServerAuthenticationDto
import org.randomcat.agorabot.config.parsing.readRelayConfig
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.IrcServerAuthentication
import org.randomcat.agorabot.setup.IrcSetupResult
import org.randomcat.agorabot.setup.connectIrc
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.readText

object IrcSetupTag : FeatureElementTag<IrcSetupResult.Connected>

private data class IrcFeatureConfig(
    val rawConfig: IrcConfig?,
    val ircStorageDir: Path,
)

private val logger = LoggerFactory.getLogger("IrcFeature")

@FeatureSourceFactory
fun ircFeatureSource(): FeatureSource<*> = object : FeatureSource<IrcFeatureConfig> {
    override val featureName: String
        get() = "irc"

    override fun readConfig(context: FeatureSetupContext): IrcFeatureConfig {
        val rawConfig = readRelayConfig(
            path = context.paths.configPath.resolve("relay.json"),
        ) { authenticationDto ->
            when (authenticationDto) {
                is RelayIrcServerAuthenticationDto.EcdsaPrivateKeyPath -> {
                    val unresolvedPath = Path.of(authenticationDto.unresolvedPath)

                    val resolvedPath = if (unresolvedPath.isAbsolute) {
                        unresolvedPath
                    } else {
                        context.paths.configPath.resolve(unresolvedPath)
                    }

                    val key = SaslEcdsaNist256PChallenge.getPrivateKey(resolvedPath.readText().trim())
                    IrcServerAuthentication.EcdsaPrivateKey(key)
                }
            }
        }

        return IrcFeatureConfig(
            rawConfig = rawConfig,
            ircStorageDir = context.paths.storagePath.resolve("irc"),
        )
    }

    override val dependencies: List<FeatureDependency<*>>
        get() = emptyList()

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(IrcSetupTag)

    override fun createFeature(config: IrcFeatureConfig, context: FeatureSourceContext): Feature {
        if (config.rawConfig == null) return Feature.singleTag(IrcSetupTag)

        when (val result = connectIrc(ircConfig = config.rawConfig, storageDir = config.ircStorageDir)) {
            is IrcSetupResult.Connected -> {
                return Feature.singleTag(IrcSetupTag, result, close = {
                    for (client in result.clients.clients) {
                        logger.info("Shutting down IRC...")
                        client.shutdown("Bot shutdown")
                        logger.info("IRC shutdown.")
                    }
                })
            }

            is IrcSetupResult.ErrorWhileConnecting -> throw result.error
        }
    }
}
