package org.randomcat.agorabot

import org.kitteh.irc.client.library.feature.auth.SaslEcdsaNist256PChallenge
import org.randomcat.agorabot.config.parsing.RelayIrcServerAuthenticationDto
import org.randomcat.agorabot.config.parsing.readRelayConfig
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.IrcServerAuthentication
import org.randomcat.agorabot.setup.IrcSetupResult
import java.nio.file.Path
import kotlin.io.path.readText

object IrcSetupTag : FeatureElementTag<IrcSetupResult>

@FeatureSourceFactory
fun ircFeatureSource() = object : FeatureSource<IrcConfig?> {
    override val featureName: String
        get() = "irc"

    override fun readConfig(context: FeatureSetupContext): IrcConfig? {
        return readRelayConfig(
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

    }

    override val dependencies: List<FeatureDependency<*>>
        get() = emptyList()

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(IrcSetupTag)

    override fun createFeature(config: IrcConfig?, context: FeatureSourceContext): Feature {
        if (config == null) return Feature.singleTag(IrcSetupTag)


    }
}
